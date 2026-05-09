## 2025-05-15 - Fix Stack Trace Leakage in VpnError
**Vulnerability:** `VpnError.fromException` was using `e.stackTraceToString()` to populate the `details` field, which is exposed to users in the UI via `getUserMessage()`. This could leak sensitive implementation details, internal class names, and library versions.
**Learning:** Error handling classes that simplify exceptions for UI display must be carefully audited to ensure they don't inadvertently pass through raw diagnostic data intended for developers.
**Prevention:** Always use `e.toString()` or a custom sanitized message for user-facing error details. Maintain unit tests that specifically check for the absence of stack trace patterns (e.g., "at package.name") in UI-bound strings.
