## 2025-11-07 - [Information Leakage in VpnError]
**Vulnerability:** `VpnError.fromException()` was leaking full internal stack traces to the UI via the `details` field.
**Learning:** Using `e.stackTraceToString()` in common error utility classes can inadvertently expose internal code structure and sensitive paths to the end user.
**Prevention:** Always sanitize exception data before passing it to the UI. Use `e.javaClass.simpleName` or a predefined mapping of user-friendly error details instead of full stack traces.
