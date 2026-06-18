## 2024-05-22 - Prevented Information Leakage in VpnError
**Vulnerability:** Information Leakage (CWE-209) via stack traces in user-facing error messages.
**Learning:** VpnError.getUserMessage() was fallbacking to details (stack trace) when creating user-friendly messages, exposing internals to the end user.
**Prevention:** Always separate internal error details (for logging) from user-friendly messages. Ensure user-facing methods only return sanitized information.
