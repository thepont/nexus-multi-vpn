## 2025-05-15 - Redacting Stack Traces from User UI
**Vulnerability:** Information Exposure Through an Error Message (CWE-209). The `VpnError.getUserMessage()` function was appending the full stack trace (from the `details` field) to strings displayed directly to the user in the UI.
**Learning:** The application architecture used a single `VpnError` data class for both internal logging and user-facing messages, but failed to distinguish between the two when generating the user-friendly string.
**Prevention:** Always separate technical error details (stack traces, internal IDs) from user-facing messages. Use a dedicated method like `getUserMessage()` to return only safe, non-technical information while preserving the technical details in separate fields for internal logging only.
