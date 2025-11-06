# PacketRouter DNS Query Fix

## Problem
DNS queries from registered apps were not being routed through the VPN tunnel because `PacketRouter` couldn't determine the UID for new connections that weren't pre-registered in `ConnectionTracker`.

## Solution
Modified `PacketRouter.routePacket()` to:
1. When a connection is not found in the tracking table, check if any packages are registered
2. If registered packages exist, immediately register the new connection and route it to the appropriate tunnel
3. This handles DNS queries and other new connections from registered apps

## Code Changes

### PacketRouter.kt
- Added logic to check `tracker.getRegisteredPackages()` when connection is not tracked
- Immediately register new connections from registered packages and route to tunnel
- Added detailed logging for debugging

### ConnectionTracker.kt  
- Added `getRegisteredPackages()` method to return all registered package names

## Status
✅ Code changes are complete and in place
⚠️ Main app APK needs to be rebuilt to test the fix
⚠️ Native library build requires vcpkg (CMake error: missing lz4 dependency)

## To Test
1. Ensure VCPKG_ROOT is set and vcpkg has lz4 installed for arm64-android
2. Rebuild main app: `./gradlew :app:assembleDebug :app:installDebug`
3. Run E2E tests and check logs for:
   - "🔍 Connection not tracked - checking X registered package(s)"
   - "✅ Registered and routed new connection"
4. Verify DNS resolution works in NordVPN E2E test

## Expected Behavior
- DNS queries from registered apps should now be routed through the VPN tunnel
- New connections from registered apps should be automatically registered and routed
- Logs should show connection registration and routing for previously untracked packets

