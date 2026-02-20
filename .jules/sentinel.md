## 2025-11-08 - Fixed Stack Trace Leakage in VpnError
**Vulnerability:** Full stack traces were being exposed to users via the VpnError class when an exception occurred during VPN operations.
**Learning:** Exposing stack traces can leak internal implementation details, such as library versions and file paths, which could be useful to an attacker.
**Prevention:** Always use e.message or a sanitized, generic error message for user-facing error details. Never use e.stackTraceToString() in a context where it might be displayed to the user.
