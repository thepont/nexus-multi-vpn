## 2025-05-15 - Fix Stack Trace Leakage in VpnError
**Vulnerability:** `VpnError.fromException` was using `e.stackTraceToString()` to populate the `details` field, which is exposed to users in the UI via `getUserMessage()`. This could leak sensitive implementation details, internal class names, and library versions.
**Learning:** Error handling classes that simplify exceptions for UI display must be carefully audited to ensure they don't inadvertently pass through raw diagnostic data intended for developers.
**Prevention:** Always use `e.toString()` or a custom sanitized message for user-facing error details. Maintain unit tests that specifically check for the absence of stack trace patterns (e.g., "at package.name") in UI-bound strings.

## 2025-05-15 - Unified VPN State Representation for E2E Reliability
**Vulnerability:** Divergent state terminology between backend (`CONNECTED`) and frontend (`PROTECTED`) led to E2E test failures when asserting UI visibility based on regex patterns. While not a direct security hole, inconsistent state reporting can mask security failures (e.g., failing to report a disconnected tunnel).
**Learning:** System-wide terminology must be unified at the data layer (Enum) to ensure predictable behavior across the entire stack, including automated testing.
**Prevention:** Use a single source of truth for all lifecycle states. Map technical states to user-friendly strings within the Enum itself using a `displayText` property.
