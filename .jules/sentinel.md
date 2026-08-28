## 2025-11-17 - Prohibit Cleartext Traffic and Enforce HTTPS GeoIP Endpoint
**Vulnerability:** GeoIpService and test suites made unencrypted HTTP calls to `http://ip-api.com/`, and `network_security_config.xml` explicitly whitelisted `ip-api.com` for cleartext traffic while `AndroidManifest.xml` enabled cleartext traffic application-wide.
**Learning:** `ip-api.com` returns HTTP 403 on HTTPS for free tier users; `free.freeipapi.com` (`https://free.freeipapi.com/api/`) provides redirect-free HTTPS access for free tier GeoIP JSON lookups.
**Prevention:** Strictly enforce `cleartextTrafficPermitted="false"` across network security configuration and use HTTPS-compatible APIs for location/ip checks.
