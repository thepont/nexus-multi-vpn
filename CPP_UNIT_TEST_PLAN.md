# C++ Unit Testing Strategy for OpenVPN Debugging

## 🎯 Goal

Add comprehensive unit tests to isolate and diagnose why OpenVPN 3's data channel isn't working with our External TUN Factory implementation.

## 🧪 Test Strategy

### Phase 1: Verify Our Code Works in Isolation

#### Test 1: Socketpair I/O
**File**: `app/src/test/cpp/socketpair_test.cpp`

Test that basic socketpair communication works:
```cpp
TEST(SocketpairTest, BasicReadWrite) {
    int fds[2];
    socketpair(AF_UNIX, SOCK_SEQPACKET, 0, fds);
    
    // Write to fd[0], read from fd[1]
    uint8_t data[] = {0x01, 0x02, 0x03, 0x04};
    write(fds[0], data, 4);
    
    uint8_t buf[4];
    ssize_t n = read(fds[1], buf, 4);
    
    EXPECT_EQ(n, 4);
    EXPECT_EQ(memcmp(data, buf, 4), 0);
}

TEST(SocketpairTest, NonBlockingBehavior) {
    // Test O_NONBLOCK flag behavior
    // Verify EAGAIN when no data available
}

TEST(SocketpairTest, PacketBoundaries) {
    // SOCK_SEQPACKET should preserve packet boundaries
    // Write 2 separate packets, read them separately
}
```

**Expected**: All pass ✅

#### Test 2: BufferAllocated Construction
**File**: `app/src/test/cpp/buffer_test.cpp`

Test OpenVPN's BufferAllocated:
```cpp
TEST(BufferTest, ConstructFromData) {
    uint8_t data[] = {0x45, 0x00, 0x00, 0x54, /* IP packet */};
    
    BufferAllocated buf(data, sizeof(data), BufAllocFlags::CONSTRUCT_ZERO);
    
    EXPECT_EQ(buf.size(), sizeof(data));
    EXPECT_EQ(memcmp(buf.c_data(), data, sizeof(data)), 0);
}

TEST(BufferTest, NoBufferFull) {
    // Ensure our construction method doesn't trigger buffer_full
    uint8_t data[1500];
    memset(data, 0xAA, sizeof(data));
    
    EXPECT_NO_THROW({
        BufferAllocated buf(data, sizeof(data), BufAllocFlags::CONSTRUCT_ZERO);
    });
}
```

**Expected**: All pass ✅ (we fixed this)

### Phase 2: Mock TunClientParent

#### Test 3: CustomTunClient with Mock Parent
**File**: `app/src/test/cpp/custom_tun_test.cpp`

Create a mock TunClientParent to test our CustomTunClient:
```cpp
class MockTunClientParent : public TunClientParent {
public:
    std::vector<std::vector<uint8_t>> received_packets;
    
    void tun_recv(BufferAllocated& buf) override {
        // Record that we received a packet
        std::vector<uint8_t> packet(buf.c_data(), buf.c_data() + buf.size());
        received_packets.push_back(packet);
    }
    
    void tun_error(const Error::Type fatal_err, const std::string& err_text) override {
        // Record errors
    }
    
    void tun_pre_tun_config() override {}
    void tun_pre_route_config() override {}
    void tun_connected() override {}
};

TEST(CustomTunTest, OutboundPacketFlow) {
    openvpn_io::io_context io_ctx;
    MockTunClientParent mock_parent;
    
    CustomTunClient tun(io_ctx, mock_parent, "test_tunnel", nullptr);
    
    // Simulate tun_start
    OptionList opts;
    opts.parse_from_config("...", nullptr);
    TransportClient trans;
    CryptoDCSettings crypto;
    
    tun.tun_start(opts, trans, crypto);
    
    // Get app_fd and write a test packet
    int app_fd = tun.getAppFd();
    uint8_t test_packet[] = {0x45, 0x00, 0x00, 0x54, /* ... */};
    write(app_fd, test_packet, sizeof(test_packet));
    
    // Run io_context to process async read
    io_ctx.run_one();
    
    // Verify mock_parent.tun_recv() was called
    EXPECT_EQ(mock_parent.received_packets.size(), 1);
    EXPECT_EQ(mock_parent.received_packets[0].size(), sizeof(test_packet));
}

TEST(CustomTunTest, InboundPacketFlow) {
    // Test tun_send() writes to lib_fd correctly
    openvpn_io::io_context io_ctx;
    MockTunClientParent mock_parent;
    
    CustomTunClient tun(io_ctx, mock_parent, "test_tunnel", nullptr);
    tun.tun_start(opts, trans, crypto);
    
    // Create a test packet
    uint8_t test_packet[] = {0x45, 0x00, 0x00, 0x54, /* ... */};
    BufferAllocated buf(test_packet, sizeof(test_packet), BufAllocFlags::CONSTRUCT_ZERO);
    
    // Call tun_send (simulates OpenVPN sending us decrypted packet)
    bool result = tun.tun_send(buf);
    
    EXPECT_TRUE(result);
    
    // Read from app_fd and verify packet arrived
    int app_fd = tun.getAppFd();
    uint8_t read_buf[2048];
    ssize_t n = read(app_fd, read_buf, sizeof(read_buf));
    
    EXPECT_EQ(n, sizeof(test_packet));
    EXPECT_EQ(memcmp(read_buf, test_packet, n), 0);
}
```

**Critical Test**: If `CustomTunTest.OutboundPacketFlow` **fails**, our code is broken.  
If it **passes**, OpenVPN isn't calling our methods correctly.

### Phase 3: Integration Test with Real OpenVPN

#### Test 4: Full OpenVPN Integration
**File**: `app/src/test/cpp/openvpn_integration_test.cpp`

```cpp
class TestOpenVPNClient : public AndroidOpenVPNClient {
public:
    std::vector<Event> received_events;
    
    void event(const Event& evt) override {
        received_events.push_back(evt);
        AndroidOpenVPNClient::event(evt);
    }
};

TEST(OpenVPNIntegrationTest, DataChannelInitialization) {
    // Set up minimal OpenVPN config
    std::string config = R"(
client
remote 127.0.0.1 1194 udp
dev tun
verb 5
    )";
    
    TestOpenVPNClient client;
    // ... set up client ...
    
    Config cfg;
    cfg.content = config;
    EvalConfig eval = client.eval_config(cfg);
    
    EXPECT_FALSE(eval.error);
    
    // Connect to test server (would need mock OpenVPN server)
    // Verify DATA_CHANNEL_STARTED or similar event
}
```

### Phase 4: Spy on OpenVPN Internals

#### Test 5: OpenVPN Method Call Verification
**File**: `app/src/test/cpp/openvpn_spy_test.cpp`

Use technique to spy on what OpenVPN calls:
```cpp
class SpyCustomTunClient : public CustomTunClient {
public:
    int tun_send_call_count = 0;
    int tun_start_call_count = 0;
    
    bool tun_send(BufferAllocated& buf) override {
        tun_send_call_count++;
        return CustomTunClient::tun_send(buf);
    }
    
    void tun_start(...) override {
        tun_start_call_count++;
        CustomTunClient::tun_start(...);
    }
};

TEST(OpenVPNSpyTest, VerifyMethodCalls) {
    // Use spy to see what OpenVPN actually calls
    // Key question: Is tun_send() EVER called?
}
```

---

## 🔍 What Tests Will Reveal

### Scenario A: Phase 2 Tests Fail
**Meaning**: Our CustomTunClient code has bugs  
**Action**: Fix our implementation  
**Probability**: Low (we've reviewed it thoroughly)

### Scenario B: Phase 2 Tests Pass, Phase 3 Fails
**Meaning**: OpenVPN 3 ClientAPI isn't calling our methods correctly  
**Action**: Bug is in OpenVPN 3's External TUN Factory integration  
**Probability**: **High** (matches our hypothesis)

### Scenario C: All Tests Pass in Isolation
**Meaning**: Works in unit tests, fails in real app  
**Action**: Environment issue (threading, timing, io_context)  
**Probability**: Medium

---

## 🛠️ Implementation Plan

### Step 1: Set Up C++ Testing Framework (30 min)
```cmake
# app/src/test/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.10)

enable_testing()
find_package(GTest REQUIRED)

add_executable(openvpn_tests
    socketpair_test.cpp
    buffer_test.cpp
    custom_tun_test.cpp
)

target_link_libraries(openvpn_tests
    GTest::GTest
    GTest::Main
    openvpn3
)
```

### Step 2: Write Basic Tests (1 hour)
- Socketpair I/O test
- Buffer construction test
- Basic sanity checks

### Step 3: Mock TunClientParent (1.5 hours)
- Create mock implementation
- Test outbound flow (app → OpenVPN)
- Test inbound flow (OpenVPN → app)

### Step 4: Analyze Results (30 min)
- Review test output
- Identify exact failure point
- Document findings

**Total Time**: ~3 hours

---

## 📊 Expected Outcomes

### Best Case
Tests reveal **exact line** where OpenVPN stops calling our methods.  
We can create targeted fix or clear bug report.

### Most Likely Case
Tests confirm our code works perfectly in isolation.  
Proves issue is in OpenVPN 3 ClientAPI's handling of External TUN Factory.  
**Strong evidence for bug report to OpenVPN project.**

### Worst Case
Tests reveal subtle bug in our code we missed.  
**We fix it and OpenVPN works!** 🎉

---

## 🎯 Decision Point

**Should we invest 3 hours in C++ unit testing?**

### Pros ✅
- Systematic, scientific approach
- Will definitively answer "is it us or OpenVPN?"
- Great for documentation and future debugging
- If we find a bug in our code, we fix it and win!
- Even if it confirms OpenVPN bug, we have proof

### Cons ❌
- 3 more hours investment
- Might just confirm what we suspect (OpenVPN 3 bug)
- WireGuard already works
- Users are waiting

### My Recommendation

**Yes, do it!** Here's why:

1. **Users are demanding OpenVPN** - you said so
2. **3 hours is reasonable** for thorough diagnosis
3. **High chance of finding something actionable**
4. **Even if it confirms OpenVPN bug**, we'll have:
   - Strong evidence for bug report
   - Clear reproduction case
   - Good faith effort documented
   - Justification for using OpenVPN 2 instead

5. **If we find OUR bug**, we win immediately!

---

## 🚀 Next Steps

If you approve, I'll:

1. **Set up GTest** in the project (30 min)
2. **Write Phase 1 & 2 tests** (2 hours)
3. **Run tests and analyze** (30 min)
4. **Report findings** with clear next steps

This will give us **definitive answers** rather than speculation.

**Shall I proceed with setting up C++ unit tests?**


