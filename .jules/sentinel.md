## 2024-06-19 - Information Leakage in VPN Error Messages
**Vulnerability:** VpnError.kt was configured to include full stack traces in user-facing error messages via the details field. This could leak internal implementation details, class names, and system state to end users.
**Learning:** Providing detailed error information is helpful for debugging but must be strictly separated from user-facing UI components. The details field should be reserved for logging and internal diagnostics.
**Prevention:** Enforce strict redaction of stack traces in UI-facing error components. User-facing messages should only contain actionable information and non-sensitive error summaries.
