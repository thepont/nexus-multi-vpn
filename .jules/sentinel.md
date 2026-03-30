# Sentinel Security Journal 🛡️

## 2025-05-22 - [Manifest Hardening and Info Leak Fix]
**Vulnerability:** The application had `android:allowBackup="true"` and `android:usesCleartextTraffic="true"` in the production manifest, and `VpnError.kt` was exposing full stack traces via `e.stackTraceToString()`.
**Learning:** Development-friendly settings (like easy backups and cleartext for local testing/APIs) were left in the main manifest instead of being isolated to debug builds. Similarly, verbose error reporting helpful for debugging was leaking into the user-facing error model.
**Prevention:** Always harden the production manifest by default and use build-type specific overrides for testing. Sanitize exception details before passing them to the UI layer.
