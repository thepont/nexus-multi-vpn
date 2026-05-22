# Sentinel Security Journal 🛡️

## 2025-05-15 - [GeoIP HTTPS Migration & Manifest Hardening]
**Vulnerability:** Use of insecure HTTP for GeoIP lookups and overly permissive `AndroidManifest.xml` settings (`allowBackup="true"`, `usesCleartextTraffic="true"`).
**Learning:** Legacy configurations from initial development phases often leave insecure defaults like HTTP-only endpoints and cleartext traffic permissions. These must be explicitly hardened for production-ready VPN applications.
**Prevention:** Always prioritize HTTPS for third-party service integrations. Enforce `allowBackup="false"` to prevent credential leakage via ADB backups in high-security apps.
