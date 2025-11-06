# Comprehensive Test Suite Overview

This document describes the four test suites for the multi-region VPN router application.

## Test Suite Structure

### Suite 1: `NordVpnE2ETest.kt` (The "Live" Test)

**Status:** ✅ Retained as-is

**Purpose:** End-to-end (E2E) "black-box" validation that the entire application pipeline works with the live, production NordVPN API and authentication system.

**What it tests:**
- Routing to UK VPN (using real NordVPN servers)
- Routing to FR VPN (using real NordVPN servers)
- Routing to Direct Internet (no rule)

**Environment:** Production NordVPN API and servers

**Key Validation:** Full production environment validation

---

### Suite 2: `LocalRoutingTest.kt` (The "Core Router" Test)

**Status:** ✅ Created - Infrastructure ready, requires test apps

**Purpose:** Verify the core `PacketRouter` logic: its ability to differentiate between multiple app packages and route them to different tunnels simultaneously.

**Environment (Docker Compose):**
- `vpn-server-uk`: OpenVPN container on subnet `10.1.0.0/24`
- `vpn-server-fr`: OpenVPN container on subnet `10.2.0.0/24`
- `http-server-uk`: Web server at `10.1.0.2` returning `"SERVER_UK"`
- `http-server-fr`: Web server at `10.2.0.2` returning `"SERVER_FR"`

**Test Behavior:**
1. **Given:** Mini-internet is running, `VpnEngineService` is active, connected to both VPN servers
2. **And:** Two dummy apps installed: `test-app-uk.apk` and `test-app-fr.apk`
3. **And:** `AppRule` database maps:
   - `com.example.testapp.uk` → "UK-VPN"
   - `com.example.testapp.fr` → "FR-VPN"
4. **When:** Test uses UI Automator to launch and click "Fetch" in both dummy apps
5. **Then:** 
   - `test-app-uk` (requesting `http://10.1.0.2`) **must** display `"SERVER_UK"`
   - `test-app-fr` (requesting `http://10.2.0.2`) **must** display `"SERVER_FR"`

**Key Validation:** Simultaneous, per-app routing works correctly

**Docker Compose File:** `app/src/androidTest/resources/docker-compose/docker-compose.routing.yaml`

---

### Suite 3: `LocalDnsTest.kt` (The "DHCP/DNS" Test)

**Status:** ✅ Created - Infrastructure ready, requires test app

**Purpose:** Verify that `VpnEngineService` correctly accepts and uses custom DNS servers provided by the VPN's "DHCP" options.

**Environment (Docker Compose):**
- `dns-server`: dnsmasq container at `10.3.0.2` resolving `test.server.local` → `10.3.0.10`
- `vpn-server-dns`: OpenVPN container on `10.3.0.0/24` with `push "dhcp-option DNS 10.3.0.2"`
- `http-server-dns`: Web server at `10.3.0.10` returning `"DNS_TEST_PASSED"`

**Test Behavior:**
1. **Given:** DNS environment is running, `VpnEngineService` is active
2. **And:** Rule maps dummy app (`test-app-dns.apk`) to `vpn-server-dns`
3. **When:** Test launches `test-app-dns` and clicks "Fetch" button requesting `http://test.server.local`
4. **Then:**
   - DNS query **must** be routed to `dns-server` (`10.3.0.2`)
   - Name **must** be resolved to `10.3.0.10`
   - HTTP request **must** succeed
   - App **must** display `"DNS_TEST_PASSED"`

**Key Validation:** DHCP DNS option handling works correctly

**Docker Compose File:** `app/src/androidTest/resources/docker-compose/docker-compose.dns.yaml`

---

### Suite 4: `LocalConflictTest.kt` (The "Stability & Isolation" Test)

**Status:** ✅ Created - Infrastructure ready, requires test apps

**Purpose:** Prove that our "user-space" (isolated proxy) router can handle network configurations that would break OS-level routing, specifically a **subnet conflict**.

**Environment (Docker Compose):**
- `vpn-server-uk` and `vpn-server-fr` **both** use the **exact same virtual subnet** (`10.8.0.0/24`)
- `http-server-uk`: Web server at `10.8.0.2` returning `"SERVER_UK"`
- `http-server-fr`: Web server at `10.8.0.3` returning `"SERVER_FR"`

**Test Behavior:**
1. **Given:** Conflict environment is running, `VpnEngineService` is active
2. **And:** Rules map:
   - `test-app-uk` → `vpn-server-uk`
   - `test-app-fr` → `vpn-server-fr`
3. **When:** `VpnConnectionManager` connects to **both** servers
4. **Then:**
   - Both connections **must succeed** (user-space proxy doesn't add OS routes)
   - When test launches both dummy apps:
     - `test-app-uk` (requesting `10.8.0.2`) **must** display `"SERVER_UK"`
     - `test-app-fr` (requesting `10.8.0.3`) **must** display `"SERVER_FR"` **simultaneously**

**Key Validation:** `PacketRouter` is truly isolated from OS and handles conflicting subnets

**Docker Compose File:** `app/src/androidTest/resources/docker-compose/docker-compose.conflict.yaml`

---

## Test Infrastructure

### Base Classes

- **`BaseLocalTest.kt`**: Base class for Docker Compose-based tests
  - Provides Docker Compose management
  - VPN service initialization
  - Database setup
  - Permission handling

### Utilities

- **`DockerComposeManager.kt`**: Utility for managing Docker Compose environments
  - Start/stop services
  - Check service status
  - Get service IP addresses

### Docker Compose Files

All Docker Compose files are located in:
```
app/src/androidTest/resources/docker-compose/
```

Files:
- `docker-compose.routing.yaml` - Multi-tunnel routing test
- `docker-compose.dns.yaml` - DNS/DHCP test
- `docker-compose.conflict.yaml` - Subnet conflict test

## Test Apps Required

The following test apps need to be created:

1. **`test-app-uk.apk`**
   - Package: `com.example.testapp.uk`
   - Features: "Fetch" button that requests `http://10.1.0.2` (or `http://10.8.0.2` for conflict test)
   - Displays response text

2. **`test-app-fr.apk`**
   - Package: `com.example.testapp.fr`
   - Features: "Fetch" button that requests `http://10.2.0.2` (or `http://10.8.0.3` for conflict test)
   - Displays response text

3. **`test-app-dns.apk`**
   - Package: `com.example.testapp.dns`
   - Features: "Fetch" button that requests `http://test.server.local`
   - Displays response text

## Running Tests

### Prerequisites

1. Docker and Docker Compose installed on host machine
2. OpenVPN server configurations created (see `docker-compose/README.md`)
3. Test apps built and available

### Run Individual Test Suite

```bash
# Run NordVPN E2E test (requires .env with credentials)
source .env
adb shell am instrument -w \
  -e NORDVPN_USERNAME "$NORDVPN_USERNAME" \
  -e NORDVPN_PASSWORD "$NORDVPN_PASSWORD" \
  -e class com.multiregionvpn.NordVpnE2ETest \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner

# Run Local Routing Test
adb shell am instrument -w \
  -e class com.multiregionvpn.LocalRoutingTest \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner

# Run Local DNS Test
adb shell am instrument -w \
  -e class com.multiregionvpn.LocalDnsTest \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner

# Run Local Conflict Test
adb shell am instrument -w \
  -e class com.multiregionvpn.LocalConflictTest \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
```

### Run All Tests

```bash
./gradlew :app:connectedAndroidTest
```

## Next Steps

1. **Create OpenVPN Server Configurations**
   - Generate server configs for each test environment
   - Set up certificate authorities
   - Configure DHCP options

2. **Create Test Apps**
   - Build simple Android apps with HTTP fetch functionality
   - Package as APKs for installation

3. **Complete Test Implementations**
   - Add UI Automator code to launch and interact with test apps
   - Implement connection status checks
   - Add full end-to-end validation

4. **CI/CD Integration**
   - Set up Docker Compose in CI environment
   - Automate test execution
   - Generate test reports

## Files Created

- ✅ `app/src/androidTest/java/com/multiregionvpn/NordVpnE2ETest.kt` (renamed from VpnRoutingTest.kt)
- ✅ `app/src/androidTest/java/com/multiregionvpn/LocalRoutingTest.kt`
- ✅ `app/src/androidTest/java/com/multiregionvpn/LocalDnsTest.kt`
- ✅ `app/src/androidTest/java/com/multiregionvpn/LocalConflictTest.kt`
- ✅ `app/src/androidTest/java/com/multiregionvpn/test/BaseLocalTest.kt`
- ✅ `app/src/androidTest/java/com/multiregionvpn/test/DockerComposeManager.kt`
- ✅ `app/src/androidTest/resources/docker-compose/docker-compose.routing.yaml`
- ✅ `app/src/androidTest/resources/docker-compose/docker-compose.dns.yaml`
- ✅ `app/src/androidTest/resources/docker-compose/docker-compose.conflict.yaml`
- ✅ `app/src/androidTest/resources/docker-compose/README.md`


