
## 2026-06-14 - Prevent Information Leakage in VPN Error Messages
**Vulnerability:** Information Exposure Through an Error Message (CWE-209). Technical details including stack traces were exposed to users through the UI.
**Learning:** Even high-level "user-friendly" error utility functions can inadvertently leak internal system state if they blindly concatenate optional "details" fields that are populated with stack traces.
**Prevention:** Explicitly redact technical detail fields from UI-bound message generation functions. Maintain a strict separation between user-facing messages and internal debug logs.

## 2026-06-14 - CI Stability Fixes for Hilt and Maestro
**Vulnerability:** Manifest merger conflicts and missing test runners.
**Learning:** Hilt initialization in CI requires explicit attribute definitions in the debug manifest (android:name, android:theme, etc.) if they are subject to tools:replace in the main manifest. Furthermore, a missing HiltTestRunner can cause driver timeouts as the test orchestration fails to initialize the application properly.
**Prevention:** Always harden the debug manifest when using tools:replace in the main manifest. Ensure HiltTestRunner is present in the androidTest source set.
