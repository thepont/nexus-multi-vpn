## 2024-05-23 - Stack Trace Redaction in Centralized Error Handling
**Vulnerability:** Information Leakage through full stack traces in `VpnError` details.
**Learning:** Centralized error conversion utilities like `VpnError.fromException` can inadvertently propagate sensitive internal implementation details (package names, line numbers, logic flow) to the UI and system broadcasts if they use `stackTraceToString()`.
**Prevention:** Always redact stack traces in production error objects. Use `e.toString()` or mapped error codes for user-facing details, and keep full traces strictly for internal logging.
