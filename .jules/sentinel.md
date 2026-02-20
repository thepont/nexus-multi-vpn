## 2025-11-08 - Fixed Stack Trace Leakage in VpnError
**Vulnerability:** Full stack traces were being exposed to users via the VpnError class when an exception occurred during VPN operations.
**Learning:** Exposing stack traces can leak internal implementation details, such as library versions and file paths, which could be useful to an attacker.
**Prevention:** Always use e.message or a sanitized, generic error message for user-facing error details. Never use e.stackTraceToString() in a context where it might be displayed to the user.

## 2025-11-08 - Disabled Application Backups
**Vulnerability:** `android:allowBackup` was set to `true`, which could allow sensitive VPN credentials to be extracted via system backups or ADB.
**Learning:** For security-sensitive applications like VPNs, backups should be disabled by default to prevent data leakage.
**Prevention:** Set `android:allowBackup="false"` in the `AndroidManifest.xml`.
