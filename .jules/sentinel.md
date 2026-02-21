# Sentinel Security Journal 🛡️

## 2025-05-24 - [Information Leakage and Insecure Backups]
**Vulnerability:** Leaking internal stack traces through user-facing error objects and enabling system backups for an app handling sensitive VPN credentials.
**Learning:** Even when documentation or memory suggests a security fix has been implemented, the actual code may contain regressions or incomplete implementations. `VpnError.fromException` was using `e.stackTraceToString()` despite claims it was sanitized. `AndroidManifest.xml` had `android:allowBackup="true"` and `android:usesCleartextTraffic="true"` which are insecure defaults for a VPN application.
**Prevention:** Always verify security configurations in `AndroidManifest.xml` against the principle of least privilege. Sanitize all exception data before exposing it to the UI layer to prevent information leakage that could aid attackers in understanding the internal structure of the application.

## 2025-05-24 - [Conflict between Manifest and Network Security Config]
**Vulnerability:** Setting `android:usesCleartextTraffic="false"` in `AndroidManifest.xml` can override or conflict with `domain-config` exceptions in `network_security_config.xml`, potentially breaking required legacy cleartext connections (like the free tier of `ip-api.com`).
**Learning:** `network_security_config.xml` is the more modern and granular way to manage cleartext traffic. If a `base-config` with `cleartextTrafficPermitted="false"` is present in the config file, setting it to `false` in the manifest is redundant and can cause issues by ignoring specific domain exceptions.
**Prevention:** Prefer using `network_security_config.xml` for cleartext traffic control. Ensure the manifest's `android:usesCleartextTraffic` does not block legitimate exceptions defined in the security config.

## 2025-05-24 - [Plaintext Credentials in Cache]
**Vulnerability:** Temporary files containing plaintext VPN credentials (username/password) were created in the app's cache directory and never deleted, leaving sensitive data on the filesystem.
**Learning:** Even internal data passing via the filesystem is risky if cleanup is not guaranteed. While cache directories are private, rooted devices or backup exploits could expose this data.
**Prevention:** Always delete temporary sensitive files as soon as they are no longer needed. Use in-memory data passing whenever possible.
