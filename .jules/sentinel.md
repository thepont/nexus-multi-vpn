## 2025-05-14 - [Information Leakage in Error Handlers]
**Vulnerability:** The `VpnError.fromException` method was using `e.stackTraceToString()` to populate its `details` field, which is subsequently broadcast to the UI and logged.
**Learning:** Standardizing error handling in a single data class (`VpnError`) is good for consistency but creates a single point of failure where a lack of sanitization can leak internal implementation details (file paths, line numbers, class names) across the entire application.
**Prevention:** Always sanitize `Throwable` objects before storing them in error entities. Use `e.javaClass.simpleName` or a predefined list of safe error messages instead of full stack traces.

## 2025-05-14 - [Environment-Specific Network Security Hardening]
**Vulnerability:** Maintaining `android:usesCleartextTraffic="true"` in production to support testing tools (like Maestro) or local services increases the attack surface for MiTM attacks.
**Learning:** Android manifests support source-set overrides. Production hardening can be maintained in `main/AndroidManifest.xml` while allowing required test traffic in `debug/AndroidManifest.xml` using `tools:replace`.
**Prevention:** Always disable cleartext traffic and backups in the main manifest. Use the `debug` source set to re-enable them specifically for development and E2E testing environments.
