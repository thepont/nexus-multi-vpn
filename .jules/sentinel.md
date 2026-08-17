# Sentinel Security Journal

## 2025-05-18 - Insecure Transport & Manifest Backup Exposure
**Vulnerability:** GeoIP service used unencrypted HTTP (`http://ip-api.com/`) which allowed potential MITM interception/tampering of geolocation data. Production AndroidManifest enabled `usesCleartextTraffic="true"` and `allowBackup="true"`.
**Learning:** `ip-api.com` free tier does not support HTTPS (returns 403 Forbidden). Migrating to `https://free.freeipapi.com/api/` allows full HTTPS encryption and `@SerializedName` mapping for country/region fields.
**Prevention:** Global cleartext traffic should be explicitly prohibited in `network_security_config.xml` and `AndroidManifest.xml`, with backup disabled (`allowBackup="false"`).
