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

# Print message
message(STATUS "C++ unit tests configured:")
message(STATUS "  - socketpair_test")

