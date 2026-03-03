## 2025-03-03 - [Preventing Information Leakage in VPN Errors]
**Vulnerability:** Exposed internal stack traces in the `VpnError` class through the `details` field.
**Learning:** Stack traces contain sensitive information about the app's structure and libraries.
**Prevention:** Avoid using `e.stackTraceToString()` in user-facing error objects. Use `e.message` or a safe summary instead.

## 2025-03-03 - [Hardening Manifest Security Settings]
**Vulnerability:** Overly permissive `allowBackup` and `usesCleartextTraffic` settings in `AndroidManifest.xml`.
**Learning:** Default settings can expose sensitive data to system backups or Man-in-the-Middle (MITM) attacks.
**Prevention:** Always set `android:allowBackup="false"` and `android:usesCleartextTraffic="false"` for security-sensitive applications, with specific exceptions in `network_security_config.xml` if needed.
