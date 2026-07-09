## 2026-07-09 - [Insecure GeoIP Provider]
**Vulnerability:** The application was using the free tier of `ip-api.com` for geolocation, which does not support HTTPS. This forced a relaxation of the `network_security_config.xml` to allow cleartext traffic, exposing user location data to sniffing.
**Learning:** Third-party API selection can directly impact the application's security posture. A "free" service that lacks HTTPS support is a hidden security cost.
**Prevention:** Explicitly verify HTTPS support during the vendor/service selection process. Use providers like `freeipapi.com` that support HTTPS for all tiers.
