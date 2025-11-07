cmake_minimum_required(VERSION 3.22.1)

project(openvpn_cpp_tests)

# Set C++ standard
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable testing
enable_testing()

# Find or fetch Google Test
include(FetchContent)
FetchContent_Declare(
    googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG v1.14.0
)
# For Windows: Prevent overriding the parent project's compiler/linker settings
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp
)

# Test 1: Socketpair basic I/O tests
add_executable(socketpair_test
    socketpair_test.cpp
)

target_link_libraries(socketpair_test
    GTest::gtest
    GTest::gtest_main
)

# Register test with CTest
add_test(NAME SocketpairTests COMMAND socketpair_test)

# Test 2: Bidirectional flow simulation (CRITICAL TEST)
add_executable(bidirectional_flow_test
    bidirectional_flow_test.cpp
)

target_link_libraries(bidirectional_flow_test
    GTest::gtest
    GTest::gtest_main
    pthread  # For std::thread
)

# Register test with CTest
add_test(NAME BidirectionalFlowTests COMMAND bidirectional_flow_test)

# Test 3: Buffer Headroom Test (covers the OpenVPN fix)
add_executable(buffer_headroom_test
    buffer_headroom_test.cpp
)

target_link_libraries(buffer_headroom_test
    GTest::gtest
    GTest::gtest_main
)

# Register test with CTest
add_test(NAME BufferHeadroomTests COMMAND buffer_headroom_test)

# Print message
message(STATUS "C++ unit tests configured:")
message(STATUS "  - socketpair_test")
message(STATUS "  - bidirectional_flow_test (CRITICAL)")
message(STATUS "  - buffer_headroom_test (OpenVPN fix coverage)")

