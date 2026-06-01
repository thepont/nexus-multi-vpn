## 2025-01-24 - Manifest Hardening and HTTPS Enforcement
**Vulnerability:** GeoIP lookups were performed over HTTP and the manifest allowed ADB backups.
**Learning:** Default Android configurations (like allowBackup) and free-tier GeoIP services (like ip-api.com) often default to insecure transport or settings.
**Prevention:** Always explicitly disable allowBackup and cleartext traffic in the production manifest while providing overrides for debug builds.
