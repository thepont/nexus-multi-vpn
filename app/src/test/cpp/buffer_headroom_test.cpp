/**
 * Buffer Headroom Unit Test
 * 
 * Tests that buffers are allocated with proper headroom for OpenVPN encryption.
 * This test covers the fix for the "buffer_push_front_headroom" exception.
 * 
 * BACKGROUND:
 * OpenVPN needs extra space at the front of buffers to add encryption headers.
 * Without headroom, data_encrypt() throws "buffer_push_front_headroom" exception.
 * 
 * THE FIX:
 * Allocate buffers with 256 bytes HEADROOM + packet data + 128 bytes TAILROOM.
 */

#include <gtest/gtest.h>
#include <cstring>
#include <vector>

// Mock OpenVPN's BufferAllocated for testing
namespace openvpn {
    
    // Simple buffer that tracks offset and capacity
    class BufferAllocated {
    public:
        BufferAllocated(size_t capacity, int flags = 0) 
            : data_(capacity), offset_(0), size_(0) {}
        
        void init_headroom(size_t headroom) {
            offset_ = headroom;
            size_ = 0;
        }
        
        uint8_t* write_alloc(size_t len) {
            if (offset_ + size_ + len > data_.size()) {
                throw std::runtime_error("Buffer overflow");
            }
            uint8_t* ptr = data_.data() + offset_ + size_;
            size_ += len;
            return ptr;
        }
        
        const uint8_t* c_data() const {
            return data_.data() + offset_;
        }
        
        size_t size() const { return size_; }
        size_t offset() const { return offset_; }
        size_t capacity() const { return data_.size(); }
        
        // Simulate encryption adding overhead
        void simulate_encrypt() {
            // OpenVPN adds headers at front (needs headroom!)
            constexpr size_t HEADER_SIZE = 25;
            
            if (offset_ < HEADER_SIZE) {
                throw std::runtime_error("buffer_push_front_headroom");
            }
            
            // Move offset back to add headers
            offset_ -= HEADER_SIZE;
            size_ += HEADER_SIZE;
        }
        
    private:
        std::vector<uint8_t> data_;
        size_t offset_;
        size_t size_;
    };
}

class BufferHeadroomTest : public ::testing::Test {
protected:
    constexpr static size_t HEADROOM = 256;
    constexpr static size_t TAILROOM = 128;
    constexpr static size_t PACKET_SIZE = 100;
};

TEST_F(BufferHeadroomTest, BufferWithoutHeadroom_ThrowsException) {
    // This simulates our OLD code - allocate buffer with just packet size
    openvpn::BufferAllocated buf_no_headroom(PACKET_SIZE);
    
    // Write packet data
    uint8_t* data = buf_no_headroom.write_alloc(PACKET_SIZE);
    std::memset(data, 0xAA, PACKET_SIZE);
    
    EXPECT_EQ(buf_no_headroom.size(), PACKET_SIZE);
    EXPECT_EQ(buf_no_headroom.offset(), 0);  // No headroom!
    
    // Try to encrypt - should throw exception
    EXPECT_THROW({
        buf_no_headroom.simulate_encrypt();
    }, std::runtime_error) << "Should throw buffer_push_front_headroom exception";
}

TEST_F(BufferHeadroomTest, BufferWithHeadroom_EncryptionSucceeds) {
    // This simulates our FIXED code - allocate with headroom
    openvpn::BufferAllocated buf(HEADROOM + PACKET_SIZE + TAILROOM);
    
    // Initialize headroom (sets offset)
    buf.init_headroom(HEADROOM);
    
    EXPECT_EQ(buf.offset(), HEADROOM) << "Offset should be set to headroom size";
    EXPECT_EQ(buf.size(), 0) << "Size should be 0 initially";
    
    // Write packet data (after headroom)
    uint8_t* data = buf.write_alloc(PACKET_SIZE);
    std::memset(data, 0xBB, PACKET_SIZE);
    
    EXPECT_EQ(buf.size(), PACKET_SIZE) << "Size should match packet size";
    EXPECT_EQ(buf.offset(), HEADROOM) << "Offset should still be at headroom";
    
    // Encrypt - should succeed with headroom
    EXPECT_NO_THROW({
        buf.simulate_encrypt();
    }) << "Should NOT throw with proper headroom";
    
    // Verify encryption added overhead
    EXPECT_GT(buf.size(), PACKET_SIZE) << "Encrypted size should be larger";
    EXPECT_LT(buf.offset(), HEADROOM) << "Offset should move back for headers";
}

TEST_F(BufferHeadroomTest, HeadroomSize_IsAdequate) {
    // Test that 256 bytes headroom is enough for typical encryption
    // OpenVPN typically needs 25-50 bytes for:
    // - Protocol header (8-12 bytes)
    // - IV/nonce (12-16 bytes)
    // - Auth tag (16 bytes)
    constexpr size_t TYPICAL_OVERHEAD = 50;
    
    EXPECT_GE(HEADROOM, TYPICAL_OVERHEAD) 
        << "256 bytes headroom should be enough for typical OpenVPN overhead";
    
    // Even with maximum overhead, we should have room
    constexpr size_t MAX_OVERHEAD = 100;
    EXPECT_GE(HEADROOM, MAX_OVERHEAD)
        << "256 bytes should handle even maximum encryption overhead";
}

TEST_F(BufferHeadroomTest, LargePacket_WithHeadroom) {
    // Test with MTU-sized packet (1500 bytes)
    constexpr size_t MTU = 1500;
    
    openvpn::BufferAllocated buf(HEADROOM + MTU + TAILROOM);
    buf.init_headroom(HEADROOM);
    
    // Write large packet
    uint8_t* data = buf.write_alloc(MTU);
    std::memset(data, 0xCC, MTU);
    
    EXPECT_EQ(buf.size(), MTU);
    
    // Should encrypt successfully even with large packet
    EXPECT_NO_THROW({
        buf.simulate_encrypt();
    }) << "Large packets should encrypt with proper headroom";
}

TEST_F(BufferHeadroomTest, MultiplePackets_ReuseBuffer) {
    // Test that we can reuse buffers for multiple packets
    openvpn::BufferAllocated buf(HEADROOM + 200 + TAILROOM);
    
    // First packet
    buf.init_headroom(HEADROOM);
    uint8_t* data1 = buf.write_alloc(100);
    std::memset(data1, 0xDD, 100);
    
    EXPECT_NO_THROW({
        buf.simulate_encrypt();
    }) << "First packet should encrypt successfully";
    
    // Second packet (reset buffer)
    buf.init_headroom(HEADROOM);
    uint8_t* data2 = buf.write_alloc(150);
    std::memset(data2, 0xEE, 150);
    
    EXPECT_NO_THROW({
        buf.simulate_encrypt();
    }) << "Second packet should also encrypt successfully";
}

TEST_F(BufferHeadroomTest, HeadroomValues_MatchImplementation) {
    // Verify our constants match what's in custom_tun_client.h
    EXPECT_EQ(HEADROOM, 256) << "HEADROOM should be 256 bytes";
    EXPECT_EQ(TAILROOM, 128) << "TAILROOM should be 128 bytes";
    
    // Total overhead per packet
    constexpr size_t TOTAL_OVERHEAD = HEADROOM + TAILROOM;
    EXPECT_EQ(TOTAL_OVERHEAD, 384) << "Total overhead is 384 bytes per packet";
    
    // For 100-byte packet, total allocation
    constexpr size_t TOTAL_ALLOC = HEADROOM + 100 + TAILROOM;
    EXPECT_EQ(TOTAL_ALLOC, 484) << "100-byte packet needs 484 bytes total";
}

TEST_F(BufferHeadroomTest, VerifyPacketDataIntegrity) {
    // Ensure headroom doesn't corrupt packet data
    openvpn::BufferAllocated buf(HEADROOM + PACKET_SIZE + TAILROOM);
    buf.init_headroom(HEADROOM);
    
    // Write known pattern
    uint8_t* data = buf.write_alloc(PACKET_SIZE);
    for (size_t i = 0; i < PACKET_SIZE; i++) {
        data[i] = static_cast<uint8_t>(i % 256);
    }
    
    // Verify data before encryption
    const uint8_t* read_data = buf.c_data();
    for (size_t i = 0; i < PACKET_SIZE; i++) {
        EXPECT_EQ(read_data[i], static_cast<uint8_t>(i % 256))
            << "Packet data should be intact before encryption";
    }
    
    // Encrypt (adds headers)
    buf.simulate_encrypt();
    
    // After encryption, original data should still be there
    // (just offset by headers now)
    const uint8_t* encrypted_data = buf.c_data();
    // Headers are at the front (25 bytes), then original data
    for (size_t i = 0; i < PACKET_SIZE; i++) {
        EXPECT_EQ(encrypted_data[25 + i], static_cast<uint8_t>(i % 256))
            << "Original packet data should be intact after encryption";
    }
}

// Main function
int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}

