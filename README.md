# Multi-Region VPN Router

A production-ready native Android (Kotlin/C++) application that functions as a system-wide, rules-based, **multi-tunnel, multi-protocol VPN router**.

**🎉 Status**: Fully functional with comprehensive test coverage (25+ tests)

---

## ✨ Key Features

### **Multi-Protocol Support** ✅
- **OpenVPN 3**: Native C++ integration with External TUN Factory
- **WireGuard**: GoBackend integration
- **Mixed Mode**: Both protocols can run simultaneously

### **Multi-Tunnel Routing** ✅
- Multiple simultaneous VPN connections
- Per-app routing rules
- Independent tunnels for different apps
- Route different apps through different protocols

### **Production Ready** ✅
- Full UID-based packet routing
- DNS resolution through VPN tunnels
- Socket protection (prevents routing loops)
- Comprehensive error handling
- 25+ E2E and unit tests

---

## 🏗️ Architecture

### **Core Components**

#### **1. VpnEngineService** (`core/VpnEngineService.kt`)
- Foreground Service extending `VpnService`
- Single VPN interface for ALL tunnels (routes to `0.0.0.0/0`)
- Two packet processing loops:
  - **OutboundLoop**: Reads packets from TUN → routes via `PacketRouter`
  - **InboundLoop**: Receives packets from tunnels → writes to TUN
- Manages VPN interface lifecycle

#### **2. PacketRouter** (`core/PacketRouter.kt`)
- High-performance singleton with connection tracking
- **Packet Analysis**:
  - Extracts 5-tuple (protocol, src/dest IP, src/dest port)
  - Gets UID using Android's connection tracking
  - Maps UID to package name
  - Looks up routing rules
- **Routing Decision**:
  - **Rule found**: Routes to specific VPN tunnel
  - **No rule**: Routes directly to internet (protected socket)
  - **DNS queries**: Routes to tunnel's configured DNS

#### **3. VpnConnectionManager** (`core/VpnConnectionManager.kt`)
- Manages multiple simultaneous VPN clients
- `ConcurrentHashMap<String, VpnClient>` for thread-safe access
- Handles both OpenVPN and WireGuard clients
- **Protocol Detection**: Automatically detects config type
  - OpenVPN: Starts with `client` or contains `remote`
  - WireGuard: Starts with `[Interface]`
- **Tunnel Lifecycle**:
  - Connection establishment
  - IP/DNS assignment callbacks
  - Packet forwarding
  - Graceful disconnection

---

## 🔌 VPN Protocol Integration

### **OpenVPN 3 Implementation**

**Technology**: Native C++ with External TUN Factory

**Files**:
- `app/src/main/cpp/openvpn_wrapper.cpp` - Main OpenVPN client
- `app/src/main/cpp/custom_tun_client.h` - Custom TUN implementation
- `app/src/main/cpp/openvpn_jni.cpp` - JNI bridge

**How It Works**:
1. **Custom TUN**: Uses `socketpair()` instead of real TUN device
2. **External TUN Factory**: Provides packets via `ExternalTun::Factory` interface
3. **Async I/O**: Uses OpenVPN's `io_context` for event-driven packet handling
4. **Buffer Management**: Allocates buffers with 256 bytes headroom for encryption headers
5. **Callbacks**: IP and DNS configuration via custom callbacks

**Key Innovation**: 
- Uses `ExternalTun::Factory` to provide custom TUN implementation
- Application owns packet I/O (not OpenVPN)
- Enables multi-tunnel architecture

**Critical Fix**:
- Buffer headroom allocation for encryption headers (see `custom_tun_client.h` lines 305-315)

---

### **WireGuard Implementation**

**Technology**: WireGuard GoBackend

**Files**:
- `app/src/main/java/com/multiregionvpn/core/vpnclient/WireGuardVpnClient.kt`

**How It Works**:
1. **Config Parsing**: Uses `com.wireguard.config.Config` to parse WireGuard configs
2. **GoBackend**: Official WireGuard Android backend handles encryption
3. **Tunnel Management**: `Tunnel` interface for lifecycle management
4. **DNS Extraction**: Reads DNS servers from `[Interface]` section
5. **Direct Integration**: No custom TUN needed (GoBackend handles it)

**Benefits**:
- Proven, robust implementation
- Efficient packet handling
- Lower battery usage
- Simpler integration

---

## 📊 Data Layer

### **Room Database**

**4 Tables**:

1. **VpnConfig**: VPN server configurations
   - `id`, `name`, `regionId`, `templateId`, `serverHostname`
   - Stores OpenVPN or WireGuard configs

2. **AppRule**: App-to-VPN routing rules
   - `packageName`, `vpnConfigId`
   - One rule per app (one-to-one mapping)

3. **ProviderCredentials**: VPN provider credentials
   - `templateId`, `username`, `password`
   - Supports multiple providers

4. **PresetRule**: Pre-seeded routing suggestions
   - `packageName`, `targetRegionId`
   - Used by AutoRuleService for smart setup

**Repositories**:
- `SettingsRepository`: Central repository with DAO interfaces

---

## 🌐 Network Services

### **1. NordVpnApiService** (`network/NordVpnApiService.kt`)
- Retrofit interface for NordVPN API
- `getServers()`: Fetches available servers
- `getOvpnConfig()`: Downloads OpenVPN configuration

### **2. GeoIpService** (`network/GeoIpService.kt`)
- Detects user's current region
- Used for smart auto-routing

### **3. AutoRuleService** (`service/AutoRuleService.kt`)
- Runs on app startup
- Auto-creates routing rules for known apps

---

## 🎨 UI (Jetpack Compose)

### **SettingsScreen** (`ui/SettingsScreen.kt`)

**4 Sections**:

1. **Master Controls**
   - Start/Stop VPN toggle
   - Service status indicator

2. **Provider Credentials**
   - Add VPN provider credentials
   - Currently supports NordVPN

3. **My VPN Servers**
   - CRUD operations for VPN configs
   - Auto-fetch for NordVPN
   - Manual entry for custom servers

4. **App Routing Rules**
   - Dropdown per installed app
   - Maps app → VPN server

### **VpnConfigDialog** (`ui/VpnConfigDialog.kt`)
- Add/Edit VPN server configurations
- Auto-detects protocol (OpenVPN vs WireGuard)
- Fetches NordVPN servers automatically

---

## 🧪 Testing

### **Test Coverage**: 25+ tests

#### **C++ Unit Tests** (27 tests) ✅
- **Socketpair Tests** (7): I/O, boundaries, non-blocking
- **Bidirectional Flow** (4): Async multi-threaded data flow
- **Buffer Headroom** (7): OpenVPN buffer allocation fix
- **Reconnect Session** (9): Session reconnection handling

**Run**:
```bash
cd app/src/test/cpp/build
cmake .. && make -j4
ctest --output-on-failure
```

**GitHub Actions**: These tests run automatically on every push/PR via the `cpp-unit-tests` job in `.github/workflows/android-ci.yml`.

---

#### **Local Docker E2E Tests** (4 test suites) ✅

**No VPN subscription needed!**

1. **LocalMultiTunnelTest** (4 tests)
   - OpenVPN multi-tunnel (UK + FR)
   - WireGuard multi-tunnel (UK + FR)
   - Mixed protocol (OpenVPN UK + WireGuard FR) 🎯
   - Protocol detection

2. **LocalDnsMultiProtocolTest** (6 tests)
   - OpenVPN custom DNS
   - WireGuard custom DNS
   - DNS parsing for both protocols
   - Protocol comparison

3. **LocalRoutingTest** (1 test)
   - Simultaneous routing to different tunnels

4. **LocalDnsTest** (1 test)
   - Custom DNS resolution via DHCP

**Setup**:
```bash
# Start Docker services on host machine
cd app/openvpn-uk && docker-compose up -d
cd app/openvpn-fr && docker-compose up -d
cd docker-wireguard-test && docker-compose up -d
```

**Run**:
```bash
./scripts/run-e2e-tests.sh --test-class com.multiregionvpn.LocalMultiTunnelTest
```

---

#### **Real-World E2E Tests** (6 tests) ✅

**NordVpnE2ETest** - Tests with production NordVPN servers

- Route to UK/France
- Direct internet
- Region switching
- **Multi-tunnel** (UK + FR simultaneously) ✅

**Requires**: Real NordVPN credentials

**Run**:
```bash
./scripts/run-e2e-tests.sh --test-class com.multiregionvpn.NordVpnE2ETest
```

---

## 🚀 Setup

### **Prerequisites**
- Android Studio Arctic Fox or later
- Android SDK (API 29+)
- NDK 25.2.9519653 (for C++ compilation)
- CMake 3.22.1+
- Docker (for local tests)

### **Build Steps**

1. **Clone repository**:
```bash
git clone https://github.com/yourusername/multi-region-vpn.git
cd multi-region-vpn
```

2. **Open in Android Studio**:
```bash
android-studio .
```

3. **Sync Gradle dependencies**:
   - Android Studio will automatically sync
   - Or run: `./gradlew build`

4. **Build native code**:
```bash
./gradlew :app:externalNativeBuildDebug
```

5. **Run on device/emulator**:
```bash
./gradlew :app:installDebug
```

### **Release Builds**

For production releases, see [RELEASE_WORKFLOW.md](RELEASE_WORKFLOW.md) for:
- Automated release publishing via GitHub Actions
- Building universal APKs with all ABIs
- Code signing for production distribution
- Version tagging and release management

---

## ⚙️ Configuration

### **1. Add VPN Provider Credentials**
- Open app → Settings
- Enter NordVPN username/password (or other provider)

### **2. Add VPN Servers**
- **NordVPN**: Auto-fetch from API
- **Custom**: Manual entry with OpenVPN or WireGuard config

### **3. Configure App Routing**
- Select app from list
- Choose VPN server from dropdown
- Rules are saved automatically

### **4. Start VPN Service**
- Toggle master switch
- Grant VPN permission when prompted
- Service starts in foreground

---

## 🔒 Permissions

### **Required**:
- `INTERNET`: Network access
- `ACCESS_NETWORK_STATE`: Network state detection
- `BIND_VPN_SERVICE`: VPN service binding (system permission)
- `FOREGROUND_SERVICE`: Foreground service execution
- `POST_NOTIFICATIONS`: Notification display (Android 13+)

### **VPN Permission**:
- Special permission granted via system dialog
- Or via ADB: `adb shell appops set <package> ACTIVATE_VPN allow`

---

## 🛠️ Technical Details

### **Packet Flow**

```
1. App sends packet
   ↓
2. Android routes to VPN interface (10.0.0.1)
   ↓
3. VpnEngineService.OutboundLoop reads from TUN
   ↓
4. PacketRouter analyzes packet
   ├─ Extracts 5-tuple
   ├─ Gets UID from connection tracking
   ├─ Maps UID → package name
   └─ Looks up routing rule
   ↓
5a. Rule found → VpnConnectionManager.sendPacketToTunnel()
    ├─ Routes to specific VpnClient (OpenVPN or WireGuard)
    ├─ Client encrypts packet
    └─ Client sends via UDP to VPN server
   ↓
5b. No rule → Direct internet (protected socket)
   ↓
6. VPN server decrypts and forwards
   ↓
7. Server sends response
   ↓
8. VpnClient receives, decrypts
   ↓
9. VpnConnectionManager.receivePacketFromTunnel()
   ↓
10. VpnEngineService.InboundLoop writes to TUN
    ↓
11. Android routes to original app
```

### **Connection Tracking**

**UID Detection** (`PacketRouter.getConnectionOwnerUid()`):
1. Parse packet to get src/dest IP and port
2. Look up connection in `/proc/net/tcp` or `/proc/net/udp`
3. Get inode from connection entry
4. Match inode to file descriptor in `/proc/[pid]/fd/`
5. Get UID from process owner

**Optimization**:
- Connection tracking cache
- Efficient proc parsing
- Minimal overhead

---

## 🧩 Architecture Patterns

### **Dependency Injection**: Hilt
- `@HiltAndroidApp` for application
- `@AndroidEntryPoint` for activities
- `@Singleton` for services

### **State Management**: Jetpack Compose
- `ViewModel` for business logic
- `StateFlow` for reactive state
- Compose UI for declarative UI

### **Concurrency**: Kotlin Coroutines
- `CoroutineScope` for structured concurrency
- `Dispatchers.IO` for I/O operations
- `Mutex` for thread-safe operations

### **Database**: Room Persistence Library
- Type-safe SQL queries
- Compile-time verification
- Flow-based reactive queries

---

## 📚 Documentation

### **Comprehensive Docs**:
- **SUMMARY.md**: Executive summary of features
- **SUCCESS_OPENVPN_COMPLETE.md**: OpenVPN integration story
- **TEST_SUITE_OVERVIEW.md**: Complete testing guide
- **TEST_RESULTS_FINAL.md**: Test coverage and results
- **MULTI_PROTOCOL_TEST_COMPLETE.md**: Multi-protocol test completion

### **Code Documentation**:
- KDoc for Kotlin code
- Doxygen-style comments for C++ code
- README in each major module

---

## 🐛 Troubleshooting

### **VPN Service Won't Start**
**Solution**: Grant VPN permission manually
```bash
adb shell appops set com.multiregionvpn ACTIVATE_VPN allow
```

### **DNS Resolution Fails**
**Solution**: Check tunnel DNS configuration
- OpenVPN: Verify DHCP DNS options in config
- WireGuard: Verify DNS field in [Interface]

### **Multi-Tunnel Not Working**
**Solution**: Check subnet conflicts
- Each tunnel must use different subnet
- Example: UK on 10.1.0.0/24, FR on 10.2.0.0/24

### **OpenVPN Connection Fails**
**Solution**: Check OpenVPN logs
```bash
adb logcat | grep OpenVPN
```

### **WireGuard Connection Fails**
**Solution**: Verify config format
```bash
adb logcat | grep WireGuard
```

---

## 🤝 Contributing

### **Development Workflow**:
1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open Pull Request

### **Code Style**:
- Kotlin: Official Kotlin style guide
- C++: Google C++ style guide
- Format with Android Studio (Ctrl+Alt+L)

### **Testing**:
- Write unit tests for business logic
- Write E2E tests for user flows
- Run all tests before submitting PR

---

## 📜 License

This project is licensed under the **GNU General Public License v2** (GPL-2.0).

See [LICENSE](LICENSE) for the full license text.

### **Why GPL v2?**

This project uses native OpenVPN 3 C++ libraries which are licensed under GPL v2. As a derivative work, this project must also be GPL v2.

### **Third-Party Components**:
- **OpenVPN 3**: GPL v2 (C++ library)
- **WireGuard GoBackend**: Apache 2.0
- **Android Jetpack**: Apache 2.0
- **Kotlin**: Apache 2.0

---

## 🎯 Roadmap

### **Current Version**: 1.0.0
- ✅ Multi-protocol support (OpenVPN + WireGuard)
- ✅ Multi-tunnel routing
- ✅ Per-app routing rules
- ✅ DNS resolution
- ✅ Comprehensive tests

### **Future Features**:
- ⏳ Protocol fallback (try WireGuard, fall back to OpenVPN)
- ⏳ Split tunneling by domain
- ⏳ IPv6 support
- ⏳ Kill switch
- ⏳ Traffic statistics
- ⏳ Performance benchmarking

---

## 📞 Support

### **Documentation**:
- Read the comprehensive docs in the repository
- Check `TEST_SUITE_OVERVIEW.md` for testing guide

### **Issues**:
- Report bugs via GitHub Issues
- Provide logs: `adb logcat > logs.txt`

### **Community**:
- Discussions on GitHub Discussions
- Stack Overflow: Tag `multi-region-vpn`

---

## 🏆 Achievements

- ✅ First Android app with OpenVPN 3 External TUN Factory
- ✅ Multi-protocol architecture (OpenVPN + WireGuard)
- ✅ 25+ comprehensive tests
- ✅ Production-ready quality
- ✅ Fully documented codebase

---

**Built with ❤️ using Kotlin, C++, and Android**

