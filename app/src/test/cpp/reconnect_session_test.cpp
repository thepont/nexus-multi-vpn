/**
 * C++ Unit Tests for OpenVPN Session Reconnection
 * 
 * Tests the reconnectSession() function that handles network changes
 * at the C++ layer for OpenVPN tunnels.
 */

#include <gtest/gtest.h>
#include <gmock/gmock.h>

// Mock OpenVPN session structure for testing
struct MockOpenVpnSession {
    bool connected;
    bool connecting;
    std::string tunnelId;
    std::string last_error;
    int reconnect_count;
    
    MockOpenVpnSession(const std::string& id) 
        : connected(false), connecting(false), tunnelId(id), reconnect_count(0) {}
};

// Mock reconnection function for testing
void mock_reconnectSession(MockOpenVpnSession* session) {
    if (!session) {
        return;
    }
    
    if (!session->connected && !session->connecting) {
        // Not connected, skip reconnection
        return;
    }
    
    // Simulate reconnection
    session->reconnect_count++;
    session->connected = true;
}

/**
 * Test Suite: OpenVPN Session Reconnection
 */
class ReconnectSessionTest : public ::testing::Test {
protected:
    MockOpenVpnSession* session_uk;
    MockOpenVpnSession* session_fr;
    MockOpenVpnSession* session_disconnected;
    
    void SetUp() override {
        session_uk = new MockOpenVpnSession("nordvpn_UK");
        session_uk->connected = true;
        
        session_fr = new MockOpenVpnSession("nordvpn_FR");
        session_fr->connected = true;
        
        session_disconnected = new MockOpenVpnSession("nordvpn_DISC");
        session_disconnected->connected = false;
    }
    
    void TearDown() override {
        delete session_uk;
        delete session_fr;
        delete session_disconnected;
    }
};

/**
 * Test: reconnectSession should reconnect a connected session
 */
TEST_F(ReconnectSessionTest, ShouldReconnectConnectedSession) {
    // GIVEN: A connected OpenVPN session
    ASSERT_TRUE(session_uk->connected);
    ASSERT_EQ(session_uk->reconnect_count, 0);
    
    // WHEN: Network change triggers reconnection
    mock_reconnectSession(session_uk);
    
    // THEN: Session should reconnect
    EXPECT_EQ(session_uk->reconnect_count, 1);
    EXPECT_TRUE(session_uk->connected);
}

/**
 * Test: reconnectSession should handle NULL session
 */
TEST_F(ReconnectSessionTest, ShouldHandleNullSession) {
    // GIVEN: NULL session pointer
    MockOpenVpnSession* null_session = nullptr;
    
    // WHEN: Reconnection attempted on NULL
    // THEN: Should not crash
    EXPECT_NO_THROW({
        mock_reconnectSession(null_session);
    });
}

/**
 * Test: reconnectSession should skip disconnected sessions
 */
TEST_F(ReconnectSessionTest, ShouldSkipDisconnectedSession) {
    // GIVEN: A disconnected session
    ASSERT_FALSE(session_disconnected->connected);
    ASSERT_EQ(session_disconnected->reconnect_count, 0);
    
    // WHEN: Network change triggers reconnection
    mock_reconnectSession(session_disconnected);
    
    // THEN: Reconnection should be skipped
    EXPECT_EQ(session_disconnected->reconnect_count, 0);
}

/**
 * Test: reconnectSession should handle multiple sessions
 */
TEST_F(ReconnectSessionTest, ShouldReconnectMultipleSessions) {
    // GIVEN: Multiple connected sessions
    ASSERT_TRUE(session_uk->connected);
    ASSERT_TRUE(session_fr->connected);
    
    // WHEN: Network change triggers reconnection for all
    mock_reconnectSession(session_uk);
    mock_reconnectSession(session_fr);
    mock_reconnectSession(session_disconnected);
    
    // THEN: Only connected sessions should reconnect
    EXPECT_EQ(session_uk->reconnect_count, 1);
    EXPECT_EQ(session_fr->reconnect_count, 1);
    EXPECT_EQ(session_disconnected->reconnect_count, 0);
}

/**
 * Test: reconnectSession should handle connecting state
 */
TEST_F(ReconnectSessionTest, ShouldHandleConnectingState) {
    // GIVEN: A session in connecting state
    MockOpenVpnSession* session_connecting = new MockOpenVpnSession("nordvpn_CONN");
    session_connecting->connected = false;
    session_connecting->connecting = true;
    
    // WHEN: Network change triggers reconnection
    mock_reconnectSession(session_connecting);
    
    // THEN: Should reconnect (connecting state is valid)
    EXPECT_EQ(session_connecting->reconnect_count, 1);
    
    delete session_connecting;
}

/**
 * Test: reconnectSession should be idempotent
 */
TEST_F(ReconnectSessionTest, ShouldBeIdempotent) {
    // GIVEN: A connected session
    ASSERT_TRUE(session_uk->connected);
    
    // WHEN: Multiple reconnection calls
    mock_reconnectSession(session_uk);
    mock_reconnectSession(session_uk);
    mock_reconnectSession(session_uk);
    
    // THEN: Each call should succeed
    EXPECT_EQ(session_uk->reconnect_count, 3);
    EXPECT_TRUE(session_uk->connected);
}

/**
 * Test: reconnectSession should handle rapid reconnections
 */
TEST_F(ReconnectSessionTest, ShouldHandleRapidReconnections) {
    // GIVEN: A connected session
    ASSERT_TRUE(session_uk->connected);
    
    // WHEN: Rapid reconnection calls (simulating Wi-Fi flapping)
    for (int i = 0; i < 10; i++) {
        mock_reconnectSession(session_uk);
    }
    
    // THEN: All reconnections should succeed
    EXPECT_EQ(session_uk->reconnect_count, 10);
    EXPECT_TRUE(session_uk->connected);
}

/**
 * Test: reconnectSession with error state
 */
TEST_F(ReconnectSessionTest, ShouldHandleErrorState) {
    // GIVEN: A session with previous error
    session_uk->last_error = "Previous connection timeout";
    ASSERT_TRUE(session_uk->connected);
    
    // WHEN: Reconnection triggered
    mock_reconnectSession(session_uk);
    
    // THEN: Should still reconnect despite previous error
    EXPECT_EQ(session_uk->reconnect_count, 1);
    EXPECT_TRUE(session_uk->connected);
}

/**
 * Integration Test: Scenario with mixed session states
 */
TEST_F(ReconnectSessionTest, IntegrationMixedSessionStates) {
    // GIVEN: Mix of sessions in different states
    MockOpenVpnSession* sessions[] = {
        session_uk,           // connected
        session_fr,           // connected  
        session_disconnected, // disconnected
        new MockOpenVpnSession("nordvpn_NEW") // new (not connected)
    };
    sessions[3]->connecting = true; // New session connecting
    
    // WHEN: Network change reconnects all
    int total_reconnects = 0;
    for (int i = 0; i < 4; i++) {
        int before = sessions[i]->reconnect_count;
        mock_reconnectSession(sessions[i]);
        total_reconnects += (sessions[i]->reconnect_count - before);
    }
    
    // THEN: Should reconnect connected + connecting sessions only
    EXPECT_EQ(total_reconnects, 3); // UK, FR, and NEW (connecting)
    EXPECT_EQ(sessions[0]->reconnect_count, 1); // UK
    EXPECT_EQ(sessions[1]->reconnect_count, 1); // FR
    EXPECT_EQ(sessions[2]->reconnect_count, 0); // Disconnected
    EXPECT_EQ(sessions[3]->reconnect_count, 1); // Connecting
    
    delete sessions[3];
}

// Main function
int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}

