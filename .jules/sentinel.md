## 2025-05-10 - CWE-209 Stack Trace & Error Message Information Leakage
**Vulnerability:** Exception stack traces and internal class names were serialized into `VpnError.details` via `e.stackTraceToString()`, and appended to user-facing UI messages in `VpnError.getUserMessage()`.
**Learning:** Returning exception details or stack traces in error classes or UI components causes internal state and execution path leakage (CWE-209).
**Prevention:** Always sanitize exception details before surfacing errors to UI or broadcast channels. Use exception class simple names for internal metadata and user-friendly messages for end users.
