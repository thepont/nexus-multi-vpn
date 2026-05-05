## 2026-05-04 - Sensitive Data Leakage in VPN Error Reporting
**Vulnerability:** Information Leakage through stack traces in `VpnError`.
**Learning:** `Throwable.stackTraceToString()` was used to populate the `details` field of `VpnError`, which is then displayed to the user in the UI. This exposes internal class names, method names, and line numbers.
**Prevention:** Always use `Throwable.toString()` or a custom sanitized message for user-facing error details. Never expose full stack traces in production UI or non-debug logs.
