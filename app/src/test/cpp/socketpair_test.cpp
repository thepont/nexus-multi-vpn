/**
 * Socketpair I/O Unit Tests
 * 
 * Tests basic socketpair functionality that CustomTunClient relies on.
 * If these fail, the problem is in our fundamental assumptions about socketpairs.
 */

#include <gtest/gtest.h>
#include <sys/socket.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <cstring>

class SocketpairTest : public ::testing::Test {
protected:
    int fds[2];
    
    void SetUp() override {
        fds[0] = -1;
        fds[1] = -1;
    }
    
    void TearDown() override {
        if (fds[0] >= 0) close(fds[0]);
        if (fds[1] >= 0) close(fds[1]);
    }
};

TEST_F(SocketpairTest, BasicCreation) {
    // Test that SOCK_SEQPACKET socketpair can be created
    int result = socketpair(AF_UNIX, SOCK_SEQPACKET, 0, fds);
    
    ASSERT_EQ(result, 0) << "socketpair() failed: " << strerror(errno);
    ASSERT_GE(fds[0], 0) << "Invalid fd[0]";
    ASSERT_GE(fds[1], 0) << "Invalid fd[1]";
}

TEST_F(SocketpairTest, BasicReadWrite) {
    // Test basic bidirectional communication
    ASSERT_EQ(socketpair(AF_UNIX, SOCK_SEQPACKET, 0, fds), 0);
    
    // Write to fd[0], read from fd[1]
    uint8_t data[] = {0x01, 0x02, 0x03, 0x04};
    ssize_t written = write(fds[0], data, sizeof(data));
    ASSERT_EQ(written, sizeof(data)) << "Write failed: " << strerror(errno);
    
    uint8_t buf[4];
    ssize_t read_bytes = read(fds[1], buf, sizeof(buf));
    ASSERT_EQ(read_bytes, sizeof(data)) << "Read failed: " << strerror(errno);
    EXPECT_EQ(memcmp(data, buf, sizeof(data)), 0) << "Data mismatch!";
}

TEST_F(SocketpairTest, BidirectionalCommunication) {
    // Test both directions work
    ASSERT_EQ(socketpair(AF_UNIX, SOCK_SEQPACKET, 0, fds), 0);
    
    // fd[0] -> fd[1]
    uint8_t data1[] = {0xAA, 0xBB};
    ASSERT_EQ(write(fds[0], data1, 2), 2);
    
    uint8_t buf1[2];
    ASSERT_EQ(read(fds[1], buf1, 2), 2);
    EXPECT_EQ(memcmp(data1, buf1, 2), 0);
    
    // fd[1] -> fd[0]
    uint8_t data2[] = {0xCC, 0xDD};
    ASSERT_EQ(write(fds[1], data2, 2), 2);
    
    uint8_t buf2[2];
    ASSERT_EQ(read(fds[0], buf2, 2), 2);
    EXPECT_EQ(memcmp(data2, buf2, 2), 0);
}

TEST_F(SocketpairTest, PacketBoundaries) {
    // SOCK_SEQPACKET must preserve packet boundaries
    ASSERT_EQ(socketpair(AF_UNIX, SOCK_SEQPACKET, 0, fds), 0);
    
    // Write 2 separate packets
    uint8_t packet1[] = {0x01, 0x02};
    uint8_t packet2[] = {0x03, 0x04, 0x05};
    
    ASSERT_EQ(write(fds[0], packet1, 2), 2);
    ASSERT_EQ(write(fds[0], packet2, 3), 3);
    
    // Read should get first packet only
    uint8_t buf[10];
    ssize_t n1 = read(fds[1], buf, sizeof(buf));
    ASSERT_EQ(n1, 2) << "Should read first packet only";
    EXPECT_EQ(memcmp(buf, packet1, 2), 0);
    
    // Second read should get second packet
    ssize_t n2 = read(fds[1], buf, sizeof(buf));
    ASSERT_EQ(n2, 3) << "Should read second packet only";
    EXPECT_EQ(memcmp(buf, packet2, 3), 0);
}

TEST_F(SocketpairTest, NonBlockingMode) {
    // Test O_NONBLOCK behavior
    ASSERT_EQ(socketpair(AF_UNIX, SOCK_SEQPACKET, 0, fds), 0);
    
    // Set non-blocking
    int flags = fcntl(fds[1], F_GETFL, 0);
    ASSERT_GE(flags, 0);
    ASSERT_EQ(fcntl(fds[1], F_SETFL, flags | O_NONBLOCK), 0);
    
    // Try to read when no data available
    uint8_t buf[10];
    ssize_t n = read(fds[1], buf, sizeof(buf));
    
    EXPECT_EQ(n, -1) << "Should return -1 when no data";
    EXPECT_TRUE(errno == EAGAIN || errno == EWOULDBLOCK) 
        << "errno should be EAGAIN or EWOULDBLOCK, got: " << strerror(errno);
}

TEST_F(SocketpairTest, LargePacket) {
    // Test with packet size similar to MTU
    ASSERT_EQ(socketpair(AF_UNIX, SOCK_SEQPACKET, 0, fds), 0);
    
    // Create 1500 byte packet (typical MTU)
    uint8_t large_packet[1500];
    for (int i = 0; i < 1500; i++) {
        large_packet[i] = i % 256;
    }
    
    ssize_t written = write(fds[0], large_packet, sizeof(large_packet));
    ASSERT_EQ(written, sizeof(large_packet)) << "Failed to write large packet";
    
    uint8_t buf[2000];
    ssize_t read_bytes = read(fds[1], buf, sizeof(buf));
    ASSERT_EQ(read_bytes, sizeof(large_packet)) << "Failed to read large packet";
    EXPECT_EQ(memcmp(large_packet, buf, sizeof(large_packet)), 0) 
        << "Large packet data mismatch";
}

TEST_F(SocketpairTest, MultiplePacketsQueued) {
    // Test queuing multiple packets before reading
    ASSERT_EQ(socketpair(AF_UNIX, SOCK_SEQPACKET, 0, fds), 0);
    
    // Write 5 packets
    for (int i = 0; i < 5; i++) {
        uint8_t packet[] = {(uint8_t)i};
        ASSERT_EQ(write(fds[0], packet, 1), 1) << "Failed to write packet " << i;
    }
    
    // Read all 5 packets
    for (int i = 0; i < 5; i++) {
        uint8_t buf[10];
        ssize_t n = read(fds[1], buf, sizeof(buf));
        ASSERT_EQ(n, 1) << "Failed to read packet " << i;
        EXPECT_EQ(buf[0], i) << "Packet " << i << " data mismatch";
    }
}

// Main function for running tests
int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}

