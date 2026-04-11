## 2024-05-23 - Prevent Information Disclosure in VPN Errors
**Vulnerability:** Information Disclosure (CWE-209). Stack traces were being leaked to the UI in the `details` field of `VpnError`.
**Learning:** Using `e.stackTraceToString()` in user-facing error objects can expose internal implementation details, such as package names and class structures, which could be useful to an attacker.
**Prevention:** Always sanitize exception data before presenting it to the user. Use only the exception message or a generic error description for user-facing details.
