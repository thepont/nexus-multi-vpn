# Sentinel Security Journal

## 2025-05-15 - [Credential File Hardening and Manifest Lockdown]
**Vulnerability:** Plaintext credentials in temporary files with default permissions; stack trace leakage in error messages; insecure manifest defaults (allowBackup=true, usesCleartextTraffic=true).
**Learning:** Even if credentials are only stored temporarily, default file permissions on Android might allow other apps with broad storage access to read them. Stack traces in VpnError objects were being broadcasted via LocalBroadcastManager, potentially exposing internal paths and logic to other components or logs.
**Prevention:** Always use `setReadable(true, true)` for sensitive temporary files. Sanitize error details to remove stack traces before broadcasting or displaying in UI. Enforce `allowBackup="false"` and `usesCleartextTraffic="false"` in the main manifest to ensure a secure baseline.
