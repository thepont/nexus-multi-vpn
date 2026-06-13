
## 2026-06-13 - Prevent Information Leakage in VPN Error Messages
**Vulnerability:** Information Exposure Through an Error Message (CWE-209). Technical details including stack traces were exposed to users through the UI.
**Learning:** Even high-level "user-friendly" error utility functions can inadvertently leak internal system state if they blindly concatenate optional "details" fields that are populated with stack traces.
**Prevention:** Explicitly redact technical detail fields from UI-bound message generation functions. Maintain a strict separation between user-facing messages and internal debug logs.
