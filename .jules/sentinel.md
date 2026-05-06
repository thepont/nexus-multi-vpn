## 2026-05-04 - Preventing Sensitive Information Leakage in Error Messages
**Vulnerability:** Use of `Throwable.stackTraceToString()` in `VpnError.fromException` was exposing internal implementation details (package names, class structures, line numbers) to the user via the `details` field.
**Learning:** Common utility methods like `stackTraceToString()` are convenient for debugging but dangerous in production-facing error objects if those objects are displayed in the UI.
**Prevention:** Always use `Throwable.toString()` or a custom sanitized message for user-facing error details. Full stack traces should only be sent to secure, internal logging systems.
