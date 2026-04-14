# Sentinel Security Journal

## 2025-05-14 - Information Disclosure in VPN Error Messages
**Vulnerability:** Information Disclosure (CWE-209) via stack traces in user-facing error details.
**Learning:** The `VpnError.fromException` method was using `Throwable.stackTraceToString()` to populate a `details` field that is directly displayed to users in the UI. This exposes internal implementation details (package names, line numbers, library versions) which can be used by an attacker to fingerprint the application and find other vulnerabilities.
**Prevention:** Always sanitize exception data before passing it to the UI layer. Only include the exception message or a generic error code. Dedicated crash reporting tools should be used for debugging instead of leaking details to the end-user.
