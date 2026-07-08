## 2026-07-07 - [Insecure GeoIP Transmission]
**Vulnerability:** The application was using `http://ip-api.com` for geolocation lookups, which transmits the user's IP and potential location data in cleartext. This allows for MitM interception and spoofing of routing decisions.
**Learning:** The free tier of `ip-api.com` does not support HTTPS (returns 403 Forbidden). Developers often stick to insecure endpoints when the familiar provider lacks free SSL.
**Prevention:** Always prioritize HTTPS-capable API providers (like `freeipapi.com`) and enforce `android:usesCleartextTraffic="false"` in the production manifest to catch these issues during development.
