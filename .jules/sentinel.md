# Sentinel's Security Journal

## 2025-05-15 - CWE-209 Information Exposure in VpnError
**Vulnerability:** The `VpnError.fromException` method was including the full stack trace of exceptions in the `details` field, which is exposed to the UI via the `getUserMessage()` method.
**Learning:** High-level error objects used for UI reporting should never include raw internal details like stack traces, as they can leak sensitive information about the application's architecture and environment.
**Prevention:** Always sanitize exception data before including it in user-facing error objects. Use `e.message` or a predefined user-friendly message instead of `e.stackTraceToString()`.
