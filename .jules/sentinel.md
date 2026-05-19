# Sentinel Security Journal

## 2025-05-14 - [Insecure GeoIP lookup via plaintext HTTP]
**Vulnerability:** The application was using `http://ip-api.com` for geographic region detection. This transmitted location data in plaintext and required a `network_security_config.xml` exception for cleartext traffic.
**Learning:** Using free tiers of some APIs often forces fallback to insecure HTTP. `ip-api.com` only provides HTTPS for paid plans.
**Prevention:** Always prefer APIs that provide HTTPS by default for free tiers (e.g., `ipwho.is`) and enforce `usesCleartextTraffic="false"` globally.
