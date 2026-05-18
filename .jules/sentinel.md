## 2026-05-18 - [Information Exposure via Stack Traces]
**Vulnerability:** Leakage of full stack traces in user-facing error messages via `e.stackTraceToString()` in `VpnError.kt`.
**Learning:** captured stack traces expose internal paths, logic flow, and dependency versions, which can be used by attackers to map the application's internals.
**Prevention:** Always use `e.toString()` or a custom sanitized message for user-facing errors. Full stack traces should only be sent to secure, internal logging systems if necessary.
