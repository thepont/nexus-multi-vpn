# Sentinel Security Journal

## 2025-05-14 - Prevent Stack Trace Exposure in VPN Errors
**Vulnerability:** Internal stack traces were being exposed to the user interface through the `VpnError` class, which used `e.stackTraceToString()`.
**Learning:** Core error handling classes often default to verbose logging or error reporting for debugging convenience, but this can leak sensitive information about the application's internal structure, file paths, and logic to the end user or logs.
**Prevention:** Always sanitize exception data before passing it to the UI layer. Only expose user-friendly messages and high-level error types. If detailed logs are needed, they should be directed to a secure logging system, not the user-facing error objects.
