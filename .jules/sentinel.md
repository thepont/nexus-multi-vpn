# Sentinel Security Journal 🛡️

## 2025-05-15 - [Vulnerability] Stack Trace Leakage in VpnError
**Vulnerability:** The `VpnError.fromException()` method was using `e.stackTraceToString()` to populate the `details` field, which is subsequently displayed to users in the UI via `getUserMessage()`.
**Learning:** Stack traces can reveal internal package structures, library versions, and potentially sensitive code logic, providing attackers with valuable information for exploitation.
**Prevention:** Always sanitize error messages intended for the UI. Use `e.message` for high-level details and keep stack traces in secure, internal logs only.

## 2025-05-15 - [Enhancement] Hardening Android Application Configuration
**Vulnerability:** The application had `android:allowBackup="true"` and `android:usesCleartextTraffic="true"` in the main manifest.
**Learning:** `allowBackup="true"` allows sensitive data (like VPN credentials in Room DB) to be extracted via `adb backup`. `usesCleartextTraffic="true"` allows unencrypted HTTP traffic globally, increasing the risk of MitM attacks.
**Prevention:** Disable backups for apps handling sensitive credentials. Enforce HTTPS by default and use `network_security_config.xml` for granular cleartext exceptions (e.g., local testing or specific APIs). Use the debug manifest to re-enable cleartext for developer tools/agents like Maestro.
