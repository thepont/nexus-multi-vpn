## 2026-07-14 - [Secure GeoIP Transition]
**Vulnerability:** Insecure Network Transmission (Cleartext HTTP) for Geolocation.
**Learning:** Transitioning to HTTPS-only Geolocation required hardening the Network Security Configuration, which uncovered that the free tier of some providers (like ip-api.com) strictly forbids HTTPS. Switching providers introduced dependencies on subdomain-specific behaviors (302 redirects on freeipapi.com vs direct access on free.freeipapi.com) and required careful Android Manifest merging to allow local mock server testing while maintaining production security.
**Prevention:** Prefer providers that support free HTTPS from the start. Use debug-specific manifest overrides to permit cleartext for local testing instead of global exceptions.
