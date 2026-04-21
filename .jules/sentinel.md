# Sentinel Security Journal

## 2026-04-21 - Information Exposure (CWE-209) in VpnError
**Vulnerability:** `VpnError.fromException` was using `e.stackTraceToString()` to populate the `details` field, which is displayed in the UI when a connection fails. This exposes internal implementation details, class names, and potentially sensitive environment information to the end user.
**Learning:** Developers often use stack traces for debugging purposes and forget to sanitize them before they reach the presentation layer. In a security-sensitive app like a VPN, this leakage can aid an attacker in fingerprinting the application.
**Prevention:** Always sanitize exception data before exposing it to the UI or external logs. Only provide the exception message or a generic user-friendly description.

## 2026-04-21 - Insecure GeoIP Lookup (MITM Risk)
**Vulnerability:** The application was using `http://ip-api.com/` for geographic region detection. This insecure transport (HTTP) allows an attacker on the same network to intercept or modify the GeoIP response, potentially misleading the app about the user's location or the VPN's effectiveness.
**Learning:** Standard GeoIP providers often charge for HTTPS access or have different endpoint structures for their secure versions. The app was explicitly allowing cleartext traffic for this domain in `network_security_config.xml`.
**Prevention:** Enforce HTTPS for all external service calls. When migrating services, verify the API endpoint structure (e.g., `ipwho.is` uses the root endpoint instead of `/json`). Use `android:usesCleartextTraffic="false"` and a restrictive `network_security_config.xml` to prevent future regressions.
