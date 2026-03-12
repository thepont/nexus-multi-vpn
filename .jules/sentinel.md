## 2026-03-12 - Information Disclosure in VpnError
**Vulnerability:** `VpnError.fromException()` was using `e.stackTraceToString()` to populate the `details` field, which is displayed to users in the UI via `getUserMessage()`.
**Learning:** Stack traces can reveal internal application structure, file paths, and library versions, which helps an attacker map the attack surface.
**Prevention:** Always sanitize exception data before exposing it to the UI. Use `e.message` or generic error categories instead of full stack traces.

## 2026-03-12 - Insecure Default Manifest Configuration
**Vulnerability:** The production manifest had `android:allowBackup="true"` and `android:usesCleartextTraffic="true"` by default.
**Learning:** VPN applications handle extremely sensitive data (credentials, keys). Allowing ADB backups can lead to credential theft if a device is temporarily accessed. Allowing cleartext traffic globally increases the risk of MitM attacks if developers accidentally use HTTP for sensitive APIs.
**Prevention:** Explicitly disable `allowBackup` and `usesCleartextTraffic` in the main manifest. Use debug-specific manifest overrides and `network_security_config.xml` to enable these features only for development/testing environments.
