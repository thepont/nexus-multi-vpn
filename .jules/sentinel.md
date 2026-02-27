## 2026-02-27 - Information Exposure in VpnError
**Vulnerability:** Stack traces were being leaked to the user interface through the `VpnError.fromException` method, which used `e.stackTraceToString()` for the `details` field.
**Learning:** Error objects shared with the UI layer should never contain raw stack traces or internal implementation details.
**Prevention:** Use sanitized error messages for user-facing fields and keep detailed stack traces restricted to internal server-side or local device logs.
