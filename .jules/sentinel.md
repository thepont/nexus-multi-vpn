# Sentinel Security Journal

## 2026-04-28 - Secure In-Memory VPN Credential Passing
**Vulnerability:** VPN credentials (username and password) were being written to plaintext temporary files in the application's `cacheDir` before being passed to the native OpenVPN layer. This exposed secrets to any entity with access to the application's private storage or via potential leakage in backups/logs.

**Learning:** OpenVPN 3 ClientAPI provides a `provide_creds()` mechanism that accepts in-memory credentials. The previous architecture used a file-based bridge which was an unnecessary and insecure legacy pattern. Kotlin's `String` objects are sufficient for passing these secrets through JNI to C++ where they can be consumed safely.

**Prevention:** Always prioritize in-memory secret handling over disk-based persistence. When using native libraries (like OpenVPN 3), leverage their built-in credential management APIs (like `provide_creds`) instead of relying on configuration file directives that point to disk locations. Sanitize JNI and native logs to ensure credential metadata (like length) is not leaked.
