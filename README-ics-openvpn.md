# ics-openvpn Integration

This project uses [ics-openvpn](https://github.com/schwabe/ics-openvpn) as a local library module for OpenVPN functionality.

## Setup

To clone and configure ics-openvpn, run:

```bash
./scripts/setup-ics-openvpn.sh
```

This script will:
1. Clone the ics-openvpn repository (v0.7.24 by default) into `libs/ics-openvpn`
2. Apply patches to convert the main module from an application to a library
3. Backup the original `build.gradle.kts` file

## Manual Setup

If you prefer to set it up manually:

1. Clone the repository:
   ```bash
   mkdir -p libs
   cd libs
   git clone --depth 1 --branch v0.7.24 https://github.com/schwabe/ics-openvpn.git
   ```

2. Apply patches:
   ```bash
   ./scripts/patch-ics-openvpn.sh
   ```

## Patches Applied

The following modifications are made to convert ics-openvpn's main module to a library:

1. Changed plugin from `com.android.application` to `com.android.library`
2. Added `namespace = "de.blinkt.openvpn"`
3. Updated `compileSdk` to 34 (matches our project)
4. Updated `minSdk` to 29 (matches our project)
5. Removed `versionCode` and `versionName` (not valid for libraries)
6. Disabled native build (`externalNativeBuild`) for simplicity
7. Removed flavor dimensions and product flavors
8. Removed flavor-specific source sets
9. Removed signing configurations
10. Simplified dependencies (removed UI-specific ones)
11. Removed ABI splits (handled by consuming app)

## Using ics-openvpn Classes

The project directly imports and uses ics-openvpn classes:

```kotlin
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.OpenVPNThread
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.VpnProfile
```

See `app/src/main/java/com/multiregionvpn/core/vpnclient/RealOpenVpnClient.kt` for the implementation.

## License

ics-openvpn is licensed under the GNU GPL v2 with additional terms. Please refer to the original repository for license details.

