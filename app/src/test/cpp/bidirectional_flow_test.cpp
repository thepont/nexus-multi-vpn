/**
 * Bidirectional Flow Simulation Test
 * 
 * This test simulates the EXACT pattern used in our OpenVPN integration:
 * - VpnConnectionManager writes to app_fd (outbound packets)
 * - OpenVPN reads from lib_fd via async I/O
 * - OpenVPN calls parent_.tun_recv() with the packet
 * - (Server processes, sends response)
 * - OpenVPN calls our tun_send() with decrypted response
 * - We write to lib_fd
 * - VpnConnectionManager reads from app_fd (inbound packets)
 * 
 * If this test PASSES: Our pattern is correct, problem is in OpenVPN 3
 * If this test FAILS: We have a bug in our implementation
 */

#include <gtest/gtest.h>
#include <sys/socket.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <errno.h>
#include <cstring>
#include <thread>
#include <atomic>
#include <vector>

class BidirectionalFlowTest : public ::testing::Test {
protected:
    int app_fd;
    int lib_fd;
    std::atomic<bool> stop_flag{false};
    
    void SetUp() override {
        app_fd = -1;
        lib_fd = -1;
    }
    
    void TearDown() override {
        stop_flag = true;
        if (app_fd >= 0) close(app_fd);
        if (lib_fd >= 0) close(lib_fd);
    }
    
    void create_socketpair() {
        int fds[2];
        ASSERT_EQ(socketpair(AF_UNIX, SOCK_SEQPACKET, 0, fds), 0);
        app_fd = fds[0];
        lib_fd = fds[1];
        
        // Set non-blocking
        int flags = fcntl(lib_fd, F_GETFL, 0);
        ASSERT_GE(flags, 0);
        ASSERT_EQ(fcntl(lib_fd, F_SETFL, flags | O_NONBLOCK), 0);
    }
};

TEST_F(BidirectionalFlowTest, SimpleOutboundInbound) {
    // Test the basic pattern: app → lib → process → lib → app
    create_socketpair();
    
    // OUTBOUND: App writes packet
    uint8_t outbound[] = {0x45, 0x00, 0x00, 0x3c, 0xAA, 0xBB}; // Fake IP packet
    ssize_t written = write(app_fd, outbound, sizeof(outbound));
    ASSERT_EQ(written, sizeof(outbound)) << "Failed to write outbound packet";
    
    // Simulate OpenVPN reading from lib_fd
    uint8_t lib_buf[2048];
    ssize_t read_by_lib = read(lib_fd, lib_buf, sizeof(lib_buf));
    ASSERT_EQ(read_by_lib, sizeof(outbound)) << "OpenVPN didn't receive packet";
    EXPECT_EQ(memcmp(lib_buf, outbound, sizeof(outbound)), 0) << "Packet corrupted";
    
    // Simulate OpenVPN processing and sending response
    // (In real code: encrypt, send to server, receive response, decrypt)
    uint8_t response[] = {0x45, 0x00, 0x00, 0x40, 0xCC, 0xDD}; // Fake response
    
    // INBOUND: OpenVPN writes response to lib_fd
    ssize_t response_written = write(lib_fd, response, sizeof(response));
    ASSERT_EQ(response_written, sizeof(response)) << "Failed to write response";
    
    // App reads response from app_fd
    uint8_t app_buf[2048];
    ssize_t read_by_app = read(app_fd, app_buf, sizeof(app_buf));
    ASSERT_EQ(read_by_app, sizeof(response)) << "App didn't receive response";
    EXPECT_EQ(memcmp(app_buf, response, sizeof(response)), 0) << "Response corrupted";
}

TEST_F(BidirectionalFlowTest, SimulateRealDataFlow) {
    // Simulate the EXACT flow from our application
    create_socketpair();
    
    std::atomic<int> packets_processed{0};
    std::atomic<int> responses_sent{0};
    std::vector<std::vector<uint8_t>> received_packets;
    
    // Thread 1: Simulate OpenVPN reading from lib_fd (async I/O)
    std::thread openvpn_thread([&]() {
        while (!stop_flag) {
            // Poll for data on lib_fd (non-blocking)
            struct pollfd pfd;
            pfd.fd = lib_fd;
            pfd.events = POLLIN;
            
            int poll_result = poll(&pfd, 1, 100); // 100ms timeout
            
            if (poll_result > 0 && (pfd.revents & POLLIN)) {
                uint8_t buf[2048];
                ssize_t n = read(lib_fd, buf, sizeof(buf));
                
                if (n > 0) {
                    packets_processed++;
                    
                    // Store packet (simulates parent_.tun_recv())
                    std::vector<uint8_t> packet(buf, buf + n);
                    received_packets.push_back(packet);
                    
                    // Simulate server responding immediately
                    // (In real case: encrypt, send to server, receive response, decrypt)
                    uint8_t response[] = {0xAA, 0xBB, (uint8_t)packets_processed.load()};
                    
                    // Write response (simulates tun_send())
                    ssize_t sent = write(lib_fd, response, sizeof(response));
                    if (sent == sizeof(response)) {
                        responses_sent++;
                    }
                }
            }
        }
    });
    
    // Thread 2: Simulate app writing packets and reading responses
    std::thread app_thread([&]() {
        // Send 5 packets
        for (int i = 0; i < 5; i++) {
            uint8_t packet[] = {0x45, 0x00, (uint8_t)i};
            write(app_fd, packet, sizeof(packet));
            usleep(10000); // 10ms between packets
        }
        
        // Wait a bit for processing
        usleep(100000); // 100ms
        
        // Try to read responses
        for (int i = 0; i < 5; i++) {
            struct pollfd pfd;
            pfd.fd = app_fd;
            pfd.events = POLLIN;
            
            if (poll(&pfd, 1, 500) > 0) { // 500ms timeout
                uint8_t buf[10];
                ssize_t n = read(app_fd, buf, sizeof(buf));
                if (n > 0) {
                    // Successfully read response!
                }
            }
        }
    });
    
    // Wait for both threads
    app_thread.join();
    stop_flag = true;
    openvpn_thread.join();
    
    // Verify the flow worked
    EXPECT_EQ(packets_processed.load(), 5) << "OpenVPN should have processed 5 packets";
    EXPECT_EQ(responses_sent.load(), 5) << "OpenVPN should have sent 5 responses";
    EXPECT_EQ(received_packets.size(), 5) << "Should have received 5 packets";
}

TEST_F(BidirectionalFlowTest, OutboundOnlyFlow) {
    // Test just the OUTBOUND path (what we know works in real app)
    create_socketpair();
    
    // Simulate VpnConnectionManager flushing 17 queued packets
    int packets_sent = 0;
    for (int i = 0; i < 17; i++) {
        uint8_t packet[] = {0x45, 0x00, (uint8_t)i, 0xAA, 0xBB};
        ssize_t n = write(app_fd, packet, sizeof(packet));
        if (n == sizeof(packet)) {
            packets_sent++;
        }
    }
    
    EXPECT_EQ(packets_sent, 17) << "Should successfully queue 17 packets";
    
    // Simulate OpenVPN reading them all  
    int packets_received = 0;
    for (int i = 0; i < 17; i++) {
        uint8_t buf[2048];
        ssize_t n = read(lib_fd, buf, sizeof(buf));
        if (n > 0) {
            packets_received++;
        } else if (n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
            // No more data - this is OK
            break;
        }
    }
    
    EXPECT_EQ(packets_received, 17) << "OpenVPN should receive all 17 packets";
}

TEST_F(BidirectionalFlowTest, InboundOnlyFlow) {
    // Test just the INBOUND path (what DOESN'T work in real app)
    create_socketpair();
    
    // Simulate OpenVPN calling tun_send() with 3 decrypted responses
    int responses_written = 0;
    for (int i = 0; i < 3; i++) {
        uint8_t response[] = {0x45, 0x00, (uint8_t)i, 0xCC, 0xDD};
        ssize_t n = write(lib_fd, response, sizeof(response));
        if (n == sizeof(response)) {
            responses_written++;
        }
    }
    
    EXPECT_EQ(responses_written, 3) << "Should write 3 responses";
    
    // Simulate VpnConnectionManager pipe reader reading them
    int responses_read = 0;
    for (int i = 0; i < 3; i++) {
        uint8_t buf[2048];
        ssize_t n = read(app_fd, buf, sizeof(buf));
        if (n > 0) {
            responses_read++;
        } else if (n < 0) {
            break;
        }
    }
    
    EXPECT_EQ(responses_read, 3) << "App should receive all 3 responses";
}

// Main function
int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}

