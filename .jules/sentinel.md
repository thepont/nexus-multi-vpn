## 2025-05-15 - [Secure Error Handling and Manifest Hardening]
**Vulnerability:** Information Leakage via Stack Traces and Insecure Manifest Configuration.
**Learning:** `VpnError.fromException` was using `e.stackTraceToString()` which exposed internal implementation details to the UI and logs. Additionally, `android:allowBackup="true"` allowed sensitive VPN credentials in the database to be extracted via system backups, and `android:usesCleartextTraffic="true"` permitted insecure HTTP communication.
**Prevention:** Always use `e.message` or generic error messages for user-facing errors. Set `android:allowBackup="false"` and `android:usesCleartextTraffic="false"` in the manifest for security-critical applications.
