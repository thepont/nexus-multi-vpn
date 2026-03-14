## 2026-03-13 - [Information Disclosure in Error Reporting]
**Vulnerability:** Internal stack traces were being leaked to the UI via the `VpnError` class when exceptions occurred.
**Learning:** Returning full stack traces to the UI can expose sensitive internal details like file paths, class names, and method structures to end-users or potential attackers.
**Prevention:** Always use `e.javaClass.simpleName` (or a generic error message) for user-visible error details instead of `e.stackTraceToString()`. Ensure that detailed technical information is restricted to internal logs.
