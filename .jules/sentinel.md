## 2025-05-15 - [Information Exposure via Stack Traces]
**Vulnerability:** The `VpnError.fromException()` method was capturing and storing the full stack trace of exceptions in its `details` field. This information could be leaked to the UI or logs.
**Learning:** Stack traces were being used as a catch-all for "error details," which is dangerous in production environments as they expose internal class names, library versions, and implementation logic.
**Prevention:** Avoid using `Throwable.stackTraceToString()` in data classes meant for UI display or general logging. Use high-level error messages instead.

## 2025-05-15 - [Insecure Manifest Defaults]
**Vulnerability:** `android:allowBackup="true"` and `android:usesCleartextTraffic="true"` were set in the main manifest.
**Learning:** Defaulting to permissive security settings in the manifest can lead to sensitive data exposure (via backups) and man-in-the-middle attacks (via cleartext traffic).
**Prevention:** Always set `android:allowBackup="false"` and `android:usesCleartextTraffic="false"` by default. Use a network security config for necessary exceptions and override in debug manifests for testing tools if needed.
