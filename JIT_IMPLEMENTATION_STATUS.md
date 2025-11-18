# JIT VPN Implementation Status

## Completed Components

### 1. Provider Foundation ✅
- **ProviderId**: Type alias for provider identifiers
- **CredentialField**: Metadata for credential fields
- **ProviderServer**: Data class for VPN servers
- **RegionRequest**: Request specification for region connections
- **ProviderTarget**: Target specification for config generation
- **AuthenticationState**: Sealed class for auth states
- **VpnProvider**: Core interface for provider implementations
- **ProviderRegistry**: Singleton registry for all providers
- **NordVpnProvider**: Stub implementation (needs full implementation)
- **ProtonVpnProvider**: Stub implementation (needs full implementation)
- **DI Integration**: Providers wired into Dagger/Hilt

### 2. Room Database Migrations ✅
- **ProviderAccountEntity**: Stores encrypted provider accounts
- **ProviderServerCacheEntity**: Caches server lists from APIs
- **AppRule Updates**: Added providerAccountId, regionCode, preferredProtocol, fallbackDirect
- **VpnConfig Updates**: Added sourceProviderId, generatedAt
- **ProviderAccountDao**: DAO for provider accounts
- **ProviderServerCacheDao**: DAO for server cache
- **Migration 1→2**: Complete migration script
- **CredentialEncryption**: Basic encryption helper (needs Android Keystore for production)

### 3. Core JIT Components ✅
- **LatencyTester**: Measures latency to servers using TCP handshake
- **PacketBufferManager**: Manages packet buffering with memory limits
- **ConnectionState**: Enum for connection lifecycle states
- **ConnectionEntry**: Data class for active connections
- **JitConnectionOrchestrator**: Orchestrates JIT connection flow

## Remaining Work

### 4. Provider Implementations ⏳
- **NordVpnProvider**: 
  - Implement `fetchServers()` using existing NordVpnApiService
  - Implement `bestServer()` with latency integration
  - Implement `generateConfig()` using VpnTemplateService
  - Implement `authenticate()` if needed
- **ProtonVpnProvider**:
  - Create ProtonVpnApiService (Retrofit interface)
  - Implement all provider methods
  - Add WireGuard key provisioning

### 5. PacketRouter Integration ⏳
- Add JIT routing logic to `routePacket()`
- When tunnel doesn't exist:
  1. Buffer packet using PacketBufferManager
  2. Trigger JitConnectionOrchestrator.ensureConnected()
  3. Wait for connection (or continue routing if already connecting)
  4. Flush buffer once connected
- Handle both legacy (vpnConfigId) and JIT (providerAccountId) routing

### 6. VpnEngineService "Armed" State ⏳
- Add `ARMED` state to VpnServiceStateTracker
- Modify `startVpn()` to support "armed" mode:
  - Establish TUN interface
  - Start PacketRouter
  - Do NOT connect tunnels eagerly
  - Wait for packets to trigger JIT connections
- Update UI to show "Armed" status

### 7. VpnConnectionManager Integration ⏳
- Integrate JitConnectionOrchestrator with VpnConnectionManager
- Update `createTunnel()` to work with JIT flow
- Ensure packet flushing happens after connection

### 8. UI Updates ⏳
- Provider account management screen
- Region selector instead of server hostname
- Connection status display (Armed, Pinging, Connecting, Connected)
- Per-tunnel status badges

### 9. Testing ⏳
- Unit tests for new components
- Integration tests for JIT flow
- E2E tests for full JIT connection lifecycle

## Architecture Notes

### JIT Flow
1. User toggles VPN to "Armed"
2. VpnEngineService starts, TUN interface established
3. App sends packet
4. PacketRouter intercepts, sees no tunnel for route
5. PacketRouter buffers packet, triggers JitConnectionOrchestrator
6. JitConnectionOrchestrator:
   - Fetches server list (or uses cache)
   - Tests latency to candidates
   - Selects best server
   - Generates config via provider
   - Connects via VpnConnectionManager
7. Once connected, buffered packets are flushed
8. Subsequent packets route directly to tunnel
9. After inactivity timeout, tunnel disconnects

### Key Design Decisions
- **Packet Buffering**: Bounded memory (64KB per flow, 10MB total)
- **Latency Testing**: TCP handshake time as proxy (ICMP requires root)
- **Server Selection**: Lowest latency wins (can add load weighting later)
- **Inactivity Timeout**: 5 minutes default
- **State Machine**: Clear states for UI feedback

## Next Steps
1. Complete NordVpnProvider implementation
2. Integrate JIT logic into PacketRouter
3. Add "Armed" state to VpnEngineService
4. Update UI for provider accounts and status
5. Write comprehensive tests


