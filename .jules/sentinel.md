## 2025-11-07 - [Information Leakage via Stack Traces]
**Vulnerability:** The `VpnError.fromException` method was explicitly using `e.stackTraceToString()` to populate error details, which were then displayed directly to the user in the UI.
**Learning:** Even well-intentioned "detailed" error messages can leak sensitive implementation details, file paths, and library versions, aiding potential attackers in reconnaissance.
**Prevention:** Use `e.toString()` or custom error mapping for user-facing messages. Reserve stack traces exclusively for internal logging (protected by ProGuard/R8 in production).

## 2025-11-07 - [Secure GeoIP Migration Constraints]
**Vulnerability:** `GeoIpService` was using `http://ip-api.com/`, transmitting location data in plaintext.
**Learning:** Many "free" APIs (like ip-api.com) restrict HTTPS to paid tiers. Hardening the app with `usesCleartextTraffic="false"` will break these services.
**Prevention:** Always verify HTTPS availability during the vendor selection phase. Migrating to `https://ipwho.is/` allowed enforcing system-wide HTTPS without losing functionality.
