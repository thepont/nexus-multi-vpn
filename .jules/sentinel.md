# Sentinel's Journal 🛡️

## 2025-05-14 - GeoIP Migration to HTTPS
**Vulnerability:** Insecure Network Transmission (CWE-319). The application used `http://ip-api.com/` for geographic region detection, transmitting IP-related data over unencrypted HTTP.
**Learning:** Many free GeoIP providers (like ip-api.com) restrict HTTPS to paid tiers, forcing developers to use insecure endpoints. freeipapi.com is a viable alternative that supports free HTTPS.
**Prevention:** Enforce `android:usesCleartextTraffic="false"` in the manifest and use `network_security_config.xml` to strictly define allowed domains, ensuring all external API calls are encrypted.
