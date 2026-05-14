# 🛡️ Sentinel Security Journal

## 2024-05-14 - Information Leakage via Stack Traces
**Vulnerability:** `VpnError.kt` was using `e.stackTraceToString()` to populate the `details` field, which was then displayed to users in the UI via `getUserMessage()`.
**Learning:** Exposing full stack traces to users can leak sensitive implementation details, library versions, and internal package structures, which could be used by an attacker to tailor exploits.
**Prevention:** Always use `e.toString()` or a custom sanitized message for user-facing error details. Maintain a balance between provide-enough-info for troubleshooting and security.

## 2024-05-14 - Insecure Default Manifest Settings
**Vulnerability:** The application had `android:allowBackup="true"` and `android:usesCleartextTraffic="true"` in the main `AndroidManifest.xml`.
**Learning:** `allowBackup="true"` allows sensitive app data (including potentially stored VPN credentials in the future) to be extracted via `adb backup`. `usesCleartextTraffic="true"` allows the app to make insecure HTTP requests, which is a risk for MITM attacks.
**Prevention:** Harden the production manifest by disabling backups and cleartext traffic. Use the `src/debug` manifest with `tools:replace` to selectively re-enable these features for testing and development environments only.
