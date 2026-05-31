# Sentinel Security Journal 🛡️

## 2025-05-22 - Credential Leakage in Error Reporting
**Vulnerability:** VPN error messages and stack traces could contain sensitive credentials (passwords, tokens) if an authentication failure occurred.
**Learning:** Default error handling in VPN clients often includes the full response or configuration details which contain secrets.
**Prevention:** Use a centralized, sanitizing factory method for all error objects. The `VpnError.create()` method uses a regex to redact sensitive keywords before the error is broadcast to the UI or logs.

## 2025-05-22 - Insecure GeoIP Lookup
**Vulnerability:** The app used an unencrypted HTTP connection to `ip-api.com` for region detection, exposing users to MitM attacks.
**Learning:** Third-party "free" APIs often default to HTTP, and developers may overlook this in networking code.
**Prevention:** Always use HTTPS for external service lookups. Migrated to `https://ipwho.is/` and enforced `usesCleartextTraffic="false"` in the production manifest.
