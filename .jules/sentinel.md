## 2026-03-30 - CWE-209 Stack Trace Leakage in VpnError
**Vulnerability:** Exception stack traces and internal class names were captured via `e.stackTraceToString()` in `VpnError.fromException()` and presented in user-facing error dialogs via `getUserMessage()`.
**Learning:** Returning `details` or raw exception stack traces in UI models risks exposing internal codebase structures and system memory state to unauthorized users or logs.
**Prevention:** Always sanitize exception handling methods to store only high-level messages or simple exception class names, and ensure `getUserMessage()` constructs user-friendly, non-sensitive strings.
