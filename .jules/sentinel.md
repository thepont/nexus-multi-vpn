# Sentinel Security Journal

## 2025-05-24 - [GeoIP HTTPS Upgrade]
**Vulnerability:** The application was using `http://ip-api.com/` to detect user geographic regions. Since `ip-api.com` requires a paid plan for HTTPS, the free tier forced unencrypted traffic, exposing user IP and location data to MitM attacks.
**Learning:** `ip-api.com` is a common choice for GeoIP, but its HTTPS restriction on the free tier makes it a security risk for production apps.
**Prevention:** Use `ipwho.is` or similar providers that offer HTTPS on their free tier. Always enforce `cleartextTrafficPermitted="false"` in `network_security_config.xml` for production domains.
