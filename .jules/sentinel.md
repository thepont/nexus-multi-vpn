## 2026-05-04 - Preventing Sensitive Information Leakage in Error Messages
**Vulnerability:** Use of `Throwable.stackTraceToString()` in `VpnError.fromException` was exposing internal implementation details (package names, class structures, line numbers) to the user via the `details` field.
**Learning:** Common utility methods like `stackTraceToString()` are convenient for debugging but dangerous in production-facing error objects if those objects are displayed in the UI.
**Prevention:** Always use `Throwable.toString()` or a custom sanitized message for user-facing error details. Full stack traces should only be sent to secure, internal logging systems.

## 2026-05-06 - Hardening Android Application Configuration
**Vulnerability:** The application had `allowBackup="true"` and `usesCleartextTraffic="true"` in the main manifest, and used an insecure HTTP GeoIP lookup service (`ip-api.com`).
**Learning:** Default Android configurations and some free APIs often favor convenience or legacy support over security. For a VPN application, these defaults must be explicitly hardened.
**Prevention:** Always disable `allowBackup` for apps handling sensitive credentials. Use `networkSecurityConfig` to restrict cleartext traffic and only use HTTPS for external lookups. Use debug manifest overrides for testing needs rather than weakening the production manifest.
