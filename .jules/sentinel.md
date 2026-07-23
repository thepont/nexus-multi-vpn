# Sentinel Security Journal

## 2026-07-23 - Cleartext Transmission and Backups Hardening
**Vulnerability:** The application allowed cleartext HTTP traffic globally via `android:usesCleartextTraffic="true"` and backup enabled via `android:allowBackup="true"` in the main `AndroidManifest.xml`. Additionally, the production network security configuration had an insecure domain exemption for `ip-api.com`, and `GeoIpService.kt` conducted GeoIP lookups over unencrypted HTTP.
**Learning:** These defaults allow potential sensitive data leakage (via Android backup) and man-in-the-middle attacks on geo-routing metadata (via HTTP GeoIP API lookup), potentially exposing user location info and allowing route manipulation.
**Prevention:** Strictly enforce `android:allowBackup="false"` and `android:usesCleartextTraffic="false"` in the production manifest, configure a hardened `network_security_config.xml` with no cleartext exceptions, and ensure any GeoIP lookups use HTTPS secure endpoints (such as `freeipapi.com` over HTTPS).
