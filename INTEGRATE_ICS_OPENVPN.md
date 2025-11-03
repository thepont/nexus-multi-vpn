# Integrating ics-openvpn Library

The `ics-openvpn` project provides a complete OpenVPN implementation for Android. We can integrate it as a Git submodule or by adding its core classes.

## Option 1: Git Submodule (Recommended)

```bash
cd /home/pont/projects/multi-region-vpn

# Add ics-openvpn as a submodule
git submodule add https://github.com/schwabe/ics-openvpn.git libs/ics-openvpn

# Update settings.gradle.kts to include it
```

Then add to `settings.gradle.kts`:
```kotlin
include(":openvpn")
project(":openvpn").projectDir = file("libs/ics-openvpn/main")
```

## Option 2: Use Core Classes Directly

The key classes we need from ics-openvpn:
- `de.blinkt.openvpn.core.OpenVPNThread` - Main OpenVPN connection thread
- `de.blinkt.openvpn.core.ConfigParser` - Parse .ovpn files
- `de.blinkt.openvpn.core.VpnProfile` - VPN profile representation
- `de.blinkt.openvpn.core.OpenVPNService` - VPN service (we have our own, but can reference)

## Option 3: Copy Core Implementation

We can extract just the OpenVPN protocol implementation classes we need.

