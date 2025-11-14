# GitHub Copilot Instructions for Multi-Region VPN Router

## Project Overview

This is a production-ready native Android (Kotlin/C++) application that functions as a system-wide, rules-based, **multi-tunnel, multi-protocol VPN router**. The application supports both OpenVPN 3 and WireGuard protocols, enabling simultaneous VPN connections with per-app routing rules.

### Core Technologies
- **Language**: Kotlin (Android UI/business logic) + C++ (OpenVPN integration)
- **Minimum SDK**: API 29 (Android 10)
- **Build System**: Gradle with Kotlin DSL
- **Native Build**: CMake 3.22.1+, NDK 25.2.9519653
- **UI Framework**: Jetpack Compose
- **DI Framework**: Hilt
- **Database**: Room Persistence Library

## Architecture Overview

### Key Components

1. **VpnEngineService** (`core/VpnEngineService.kt`)
   - Foreground Service extending `VpnService`
   - Manages single VPN interface for all tunnels
   - Runs two packet processing loops (Outbound/Inbound)

2. **PacketRouter** (`core/PacketRouter.kt`)
   - High-performance singleton with connection tracking
   - Routes packets based on UID-to-app mapping
   - Extracts 5-tuple and applies routing rules

3. **VpnConnectionManager** (`core/VpnConnectionManager.kt`)
   - Manages multiple simultaneous VPN clients
   - Thread-safe `ConcurrentHashMap<String, VpnClient>`
   - Auto-detects protocol (OpenVPN vs WireGuard)

### Protocol Implementations

#### OpenVPN 3
- **Location**: `app/src/main/cpp/`
- **Key Files**: `openvpn_wrapper.cpp`, `custom_tun_client.h`, `openvpn_jni.cpp`
- **Architecture**: Uses External TUN Factory with `socketpair()` instead of real TUN device
- **Critical**: Buffer headroom allocation (256 bytes) for encryption headers

#### WireGuard
- **Location**: `app/src/main/java/com/multiregionvpn/core/vpnclient/WireGuardVpnClient.kt`
- **Backend**: Official WireGuard GoBackend
- **Integration**: Uses `com.wireguard.config.Config` for parsing

## Code Style Guidelines

### General Principles
Follow the established [CODE_STYLE_GUIDE.md](../CODE_STYLE_GUIDE.md) for all code changes:

1. **Comments**: Be direct and descriptive, avoid defensive language
2. **Avoid**: Urgency markers (CRITICAL, IMPORTANT, WARNING)
3. **Avoid**: Comparative language ("Instead of...", "The CORRECT way...")
4. **Focus**: State what the code does, not what it doesn't do

### Language-Specific Guidelines

#### Kotlin
- Follow official Kotlin style guide
- Use KDoc for public API documentation
- Prefer coroutines over threads
- Use `StateFlow` for reactive state management
- Format with Android Studio (Ctrl+Alt+L)

#### C++
- Follow Google C++ style guide
- Use Doxygen-style comments for public APIs
- Keep comments concise and professional
- Document complex algorithms and non-obvious design decisions

#### JNI
- Always handle exceptions properly
- Clean up local references
- Use `GetEnv()` safely in multi-threaded contexts
- Document the Java/C++ boundary clearly

## Development Workflow

### Building the Project

```bash
# Build native code
./gradlew :app:externalNativeBuildDebug

# Build APK
./gradlew :app:assembleDebug

# Install to device/emulator
./gradlew :app:installDebug
```

### Running Tests

The project has comprehensive test coverage across multiple tiers:

#### 1. C++ Unit Tests (18 tests)
```bash
cd app/src/test/cpp/build
cmake .. && make -j4
ctest --output-on-failure
```

Tests cover:
- Socketpair I/O operations
- Bidirectional async data flow
- Buffer headroom allocation

#### 2. Local Docker E2E Tests (No VPN subscription needed)
```bash
# Start Docker services first
cd app/openvpn-uk && docker-compose up -d
cd app/openvpn-fr && docker-compose up -d
cd docker-wireguard-test && docker-compose up -d

# Run tests
./scripts/run-e2e-tests.sh --test-class com.multiregionvpn.LocalMultiTunnelTest
```

Test suites:
- `LocalMultiTunnelTest` (4 tests) - Multi-tunnel, mixed protocol
- `LocalDnsMultiProtocolTest` (6 tests) - DNS for both protocols
- `LocalRoutingTest` (1 test) - Simultaneous routing
- `LocalDnsTest` (1 test) - Custom DNS via DHCP

#### 3. Real-World E2E Tests (Requires NordVPN credentials)
```bash
./scripts/run-e2e-tests.sh --test-class com.multiregionvpn.NordVpnE2ETest
```

### Testing Requirements

When making code changes:
1. **Always** run existing tests before making changes
2. Write unit tests for business logic changes
3. Write E2E tests for new user-facing features
4. Ensure C++ changes include native unit tests
5. Use local Docker tests to validate protocol changes
6. Run all relevant tests before submitting PR

## Permissions & Android Requirements

### Required Permissions
- `INTERNET` - Network access
- `ACCESS_NETWORK_STATE` - Network state detection
- `BIND_VPN_SERVICE` - VPN service binding
- `FOREGROUND_SERVICE` - Foreground service execution
- `POST_NOTIFICATIONS` - Notifications (Android 13+)

### VPN Permission
Grant via ADB for testing:
```bash
adb shell appops set com.multiregionvpn ACTIVATE_VPN allow
```

## Key Implementation Details

### Packet Flow
1. App sends packet → Android routes to VPN interface
2. `VpnEngineService.OutboundLoop` reads from TUN
3. `PacketRouter` analyzes packet (extracts 5-tuple, gets UID, maps to package)
4. Route decision: Rule found → specific tunnel; No rule → direct internet
5. `VpnClient` encrypts and sends to VPN server
6. Response received, decrypted by `VpnClient`
7. `VpnEngineService.InboundLoop` writes to TUN
8. Android routes back to original app

### Connection Tracking
- UID detection via `/proc/net/tcp` and `/proc/net/udp`
- Connection tracking cache for performance
- Minimal overhead design

### Critical Areas

#### Buffer Headroom (OpenVPN)
When modifying OpenVPN C++ code, always ensure buffer allocations include headroom:
```cpp
// Allocate buffer with 256 bytes headroom for encryption headers
buf = asio::mutable_buffer(frame->prepare(256 + size), size);
```

#### Thread Safety
- `VpnConnectionManager` uses thread-safe collections
- `PacketRouter` uses connection tracking cache
- Proper synchronization for multi-tunnel access

#### DNS Resolution
- OpenVPN: DHCP DNS options from server config
- WireGuard: DNS field in [Interface] section
- Each tunnel has independent DNS configuration

## Dependencies & Libraries

### Core Dependencies
- OpenVPN 3: GPL v2 (native C++ library)
- WireGuard GoBackend: Apache 2.0
- Android Jetpack: Apache 2.0
- Kotlin: Apache 2.0
- Hilt: Dependency injection
- Room: Database persistence
- Retrofit: Network API client

### License Compliance
This project is GPL v2 due to OpenVPN 3 dependency. Ensure all contributions comply with GPL v2 requirements.

## Common Tasks

### Adding a New VPN Protocol
1. Create new `VpnClient` implementation in `core/vpnclient/`
2. Update `VpnConnectionManager.createClient()` for protocol detection
3. Add protocol-specific configuration parsing
4. Implement packet send/receive methods
5. Add unit tests and E2E tests (local + real-world)

### Adding Routing Rules
1. Update `AppRule` entity if needed
2. Modify `PacketRouter.routePacket()` logic
3. Update UI in `SettingsScreen.kt`
4. Add tests to validate new routing behavior

### Modifying Native Code
1. Update C++ source in `app/src/main/cpp/`
2. Add/update native unit tests in `app/src/test/cpp/`
3. Rebuild: `./gradlew :app:externalNativeBuildDebug`
4. Test with local Docker E2E tests

## Troubleshooting

### VPN Service Won't Start
Grant VPN permission: `adb shell appops set com.multiregionvpn ACTIVATE_VPN allow`

### DNS Resolution Fails
Check tunnel DNS configuration in logs: `adb logcat | grep -E "OpenVPN|WireGuard|DNS"`

### Multi-Tunnel Issues
Verify each tunnel uses different subnet (e.g., UK: 10.1.0.0/24, FR: 10.2.0.0/24)

### Build Failures
Ensure correct NDK version (25.2.9519653) and CMake (3.22.1+) are installed

## Documentation

Comprehensive documentation is available:
- `README.md` - Main project documentation
- `CODE_STYLE_GUIDE.md` - Comment and style guidelines
- `TEST_SUITE_OVERVIEW.md` - Complete testing guide
- `SUMMARY.md` - Executive summary
- `SUCCESS_OPENVPN_COMPLETE.md` - OpenVPN integration story

## Code Generation Guidelines

When generating code:
1. **Respect existing patterns**: Follow established architecture and naming conventions
2. **Minimal changes**: Make the smallest change that accomplishes the goal
3. **Test coverage**: Include tests for new functionality
4. **Documentation**: Update relevant documentation files
5. **Thread safety**: Consider multi-threaded access patterns
6. **Error handling**: Follow existing error handling patterns
7. **Logging**: Use appropriate log levels and messages
8. **Comments**: Follow CODE_STYLE_GUIDE.md - direct, concise, professional

## Priority Areas

When assisting with code:
1. Maintain GPL v2 compliance
2. Preserve thread safety in multi-tunnel scenarios
3. Ensure proper buffer management in native code
4. Validate DNS configuration for both protocols
5. Maintain test coverage
6. Follow established code style
7. Keep architecture patterns consistent
