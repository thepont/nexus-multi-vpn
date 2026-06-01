## 2025-01-24 - Prevent stack trace leakage in VpnError
**Vulnerability:** Core `VpnError` data class was capturing and potentially displaying full exception stack traces to users via `e.stackTraceToString()`.
**Learning:** Standard Kotlin `Throwable` extension functions like `stackTraceToString()` are convenient for debugging but dangerous in production as they expose internal application structure and logic.
**Prevention:** Always sanitize exceptions before storing or displaying them. Use `e.message` or `e.toString()` instead of the full stack trace for user-facing error details.
