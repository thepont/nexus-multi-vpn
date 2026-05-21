## 2026-05-21 - [CRITICAL] Hardcoded Credentials in Tests
**Vulnerability:** Real NordVPN service credentials were hardcoded in `RealUserCanDoTest.kt`.
**Learning:** Credentials were left in the codebase for convenience during UI test development, but they were committed to the repository.
**Prevention:** Always use `InstrumentationRegistry.getArguments()` or build configuration fields to pass secrets to tests at runtime, never hardcode them in source files.
