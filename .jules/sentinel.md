# Sentinel Security Journal 🛡️

## 2025-05-24 - [Information Leakage and Insecure Backups]
**Vulnerability:** Leaking internal stack traces through user-facing error objects and enabling system backups for an app handling sensitive VPN credentials.
**Learning:** Even when documentation or memory suggests a security fix has been implemented, the actual code may contain regressions or incomplete implementations. `VpnError.fromException` was using `e.stackTraceToString()` despite claims it was sanitized. `AndroidManifest.xml` had `android:allowBackup="true"` and `android:usesCleartextTraffic="true"` which are insecure defaults for a VPN application.
**Prevention:** Always verify security configurations in `AndroidManifest.xml` against the principle of least privilege. Sanitize all exception data before exposing it to the UI layer to prevent information leakage that could aid attackers in understanding the internal structure of the application.
