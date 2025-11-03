# Using ics-openvpn Library

The `ics-openvpn` project provides a complete, battle-tested OpenVPN implementation. Here are the options:

## Option 1: Use JitPack (If Available)

Try adding via JitPack (may not work as it's not published):
```kotlin
implementation("com.github.schwabe:ics-openvpn:master-SNAPSHOT")
```

## Option 2: Clone and Use as Local Module (Recommended)

Since the project isn't a Maven dependency, we can clone it and use specific classes:

```bash
# Clone ics-openvpn
cd /home/pont/projects/multi-region-vpn
git clone https://github.com/schwabe/ics-openvpn.git libs/ics-openvpn

# The core OpenVPN implementation is in:
# libs/ics-openvpn/main/src/main/java/de/blinkt/openvpn/core/
```

Key classes we need:
- `OpenVPNThread.java` - The main OpenVPN connection handler
- `ConfigParser.java` - Parse .ovpn configuration files  
- `VpnProfile.java` - VPN profile representation

## Option 3: Copy Core Classes

We can copy just the OpenVPN protocol implementation classes we need into our project.

## Current Approach

For now, we have a basic implementation in `RealOpenVpnClient.kt` that:
- Parses OpenVPN configs
- Establishes SSL/TLS connections
- Handles authentication
- Has structure for packet encryption

**Recommendation**: Use Option 2 - clone ics-openvpn and integrate `OpenVPNThread` which handles the full protocol.

