# Sentinel Security Journal 🛡️

## 2025-05-15 - Hardcoded Credentials in UI Tests
**Vulnerability:** Hardcoded NordVPN service credentials found in `RealUserCanDoTest.kt`.
**Learning:** Even if the credentials are "service credentials" for testing, hardcoding them in the source code violates basic security principles and can lead to credential leakage if the repository is shared or exposed.
**Prevention:** Always use `InstrumentationRegistry.getArguments()` or other dynamic mechanisms to pass secrets to instrumentation tests. Use a `.env` file for local development and CI secrets for automated pipelines.

## 2025-05-15 - CWE-209 Information Disclosure in VpnError
**Vulnerability:** `VpnError.fromException` was using `e.stackTraceToString()` to populate the `details` field, which is displayed to the user in the UI.
**Learning:** Leaking stack traces to the user interface provides an attacker with internal implementation details, which can be used to craft more specific attacks.
**Prevention:** Sanitize all data displayed to the user. Use `e.message` or high-level error descriptions instead of full stack traces for UI-facing error objects.
