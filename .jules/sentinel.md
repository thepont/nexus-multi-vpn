# Sentinel Security Journal

## 2025-11-07 - [Insecure Communication and Data Leakage]
**Vulnerability:** Core geolocation services (`GeoIpService`) used cleartext HTTP, and `VpnError` exposed full stack traces to the UI. Credential lengths were also being logged in native layers.
**Learning:** Defaulting to HTTP for "harmless" APIs like geolocation can compromise user privacy and enable MITM attacks in a security-sensitive app like a VPN. Verbose error handling often leaks implementation details.
**Prevention:** Always enforce HTTPS for all network requests. Sanitize error messages before they reach the UI or public logs. Avoid logging sensitive metadata like credential lengths.
