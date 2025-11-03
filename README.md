# Multi-Region VPN Router

A native Android (Kotlin) application that functions as a system-wide, rules-based, multi-tunnel VPN router.

## Architecture

### Core Components

1. **VpnEngineService** (`core/VpnEngineService.kt`)
   - Foreground Service extending `VpnService`
   - Routes all device traffic (routes to `0.0.0.0/0`)
   - Runs two coroutines:
     - `OutboundLoop`: Reads packets from TUN interface
     - `InboundLoop`: Writes packets to TUN interface
   - Passes outbound packets to `PacketRouter`

2. **PacketRouter** (`core/PacketRouter.kt`)
   - High-performance singleton
   - Parses packets to extract 5-tuple (protocol, src/dest IP, src/dest port)
   - Gets UID using connection tracking
   - Gets package name from UID
   - Looks up rules in `AppRuleRepository`
   - Routes packets:
     - **Rule found**: Routes to `VpnConnectionManager.sendPacketToTunnel()`
     - **No rule**: Sends to direct internet via `VpnService.protect()`

3. **VpnConnectionManager** (`core/VpnConnectionManager.kt`)
   - Manages multiple simultaneous OpenVPN connections
   - Uses `ConcurrentHashMap<String, NativeOpenVpnClient>`
   - Forwards packets to correct native OpenVPN client

### Data Layer

**Room Database** with 4 tables:
- `VpnConfig`: VPN server configurations (id, name, regionId, templateId, serverHostname)
- `AppRule`: App-to-VPN routing rules (packageName, vpnConfigId)
- `ProviderCredentials`: VPN provider tokens (templateId, token)
- `PresetRule`: Pre-seeded app routing suggestions (packageName, targetRegionId)

**Repositories**:
- `SettingsRepository`: Central repository providing all DAO interfaces

### Services

1. **NordVpnApiService** (`network/NordVpnApiService.kt`)
   - Retrofit interface for NordVPN API
   - `getServers()`: Fetches available servers
   - `getOvpnConfig()`: Downloads OpenVPN configuration

2. **GeoIpService** (`network/GeoIpService.kt`)
   - Detects user's current region using ip-api.com
   - Used by `AutoRuleService` for smart setup

3. **AutoRuleService** (`service/AutoRuleService.kt`)
   - Runs on app startup
   - Auto-creates routing rules for known apps:
     - Detects user region
     - Checks installed apps against preset rules
     - Creates rules if user has matching VPN config

### UI (Jetpack Compose)

**SettingsScreen** (`ui/SettingsScreen.kt`):
- Section 1: Master Controls (Start/Stop VPN toggle)
- Section 2: Provider Credentials (NordVPN token input)
- Section 3: My VPN Servers (CRUD operations)
- Section 4: App Routing Rules (Dropdown per installed app)

**VpnConfigDialog** (`ui/VpnConfigDialog.kt`):
- Add/Edit VPN server dialog
- Fields: Name, Provider (NordVPN/Custom), Region
- Auto-fetches NordVPN server when provider is "NordVPN"

## Setup

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Build and run on Android device (API 29+)

## Configuration

1. Add NordVPN Access Token in Settings
2. Add VPN servers (or use auto-fetch for NordVPN)
3. Configure app routing rules
4. Start VPN Engine Service

## Implementation Notes

### Native OpenVPN Integration
The `NativeOpenVpnClient` class is a placeholder. For production:
- Integrate with a native OpenVPN library (e.g., via JNI)
- Implement proper connection management
- Handle reconnection logic

### Packet UID Detection
The `PacketRouter.getConnectionOwnerUid()` method is simplified. For production:
- Implement connection tracking table
- Parse `/proc/net/tcp` and `/proc/net/udp`
- Use file descriptor connection tracking

### NordVPN Server Auto-Fetch
The dialog currently uses a placeholder for server fetching. For production:
- Implement async server fetching in ViewModel
- Update UI with loading states
- Handle errors gracefully

## Permissions

- `INTERNET`: Network access
- `ACCESS_NETWORK_STATE`: Network state checking
- `BIND_VPN_SERVICE`: VPN service binding
- `FOREGROUND_SERVICE`: Foreground service execution
- `POST_NOTIFICATIONS`: Notification display

## License

This project is licensed under the **GNU General Public License v2** (GPL-2.0).

This license is required because the project incorporates code from
[ics-openvpn](https://github.com/schwabe/ics-openvpn), which is licensed
under the GNU GPL v2 with additional terms.

See [LICENSE](LICENSE) for the full GPL v2 license text.

### Third-Party Licenses

- **ics-openvpn**: GNU GPL v2 with additional terms
  - Located in: `libs/ics-openvpn/`
  - License: See `libs/ics-openvpn/doc/LICENSE.txt`
