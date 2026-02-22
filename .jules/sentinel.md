## 2025-05-15 - [Secure Error Handling and Manifest Hardening]
**Vulnerability:** Information Leakage via Stack Traces and Insecure Manifest Configuration.
**Learning:** `VpnError.fromException` was using `e.stackTraceToString()` which exposed internal implementation details to the UI and logs. Additionally, `android:allowBackup="true"` allowed sensitive VPN credentials in the database to be extracted via system backups, and `android:usesCleartextTraffic="true"` permitted insecure HTTP communication.
**Prevention:** Always use `e.message` or generic error messages for user-facing errors. Set `android:allowBackup="false"` and `android:usesCleartextTraffic="false"` in the manifest for security-critical applications.

## 2025-05-15 - [Sensitive File Leakage and Private API Reflection]
**Vulnerability:** Sensitive Credentials Left in Cache and Private API Usage.
**Learning:** `VpnTemplateService` wrote VPN credentials to a temporary file which was never deleted, leaving sensitive data in the application's cache directory. Also, `NativeOpenVpnClient.kt` was using reflection to access the private `mFd` field of `ParcelFileDescriptor`, which is both a security risk (bypassing visibility) and causes build failures on newer Android versions.
**Prevention:** Always delete sensitive temporary files immediately after use. Use public APIs like `parcelFileDescriptor.fd` instead of reflection whenever possible.
