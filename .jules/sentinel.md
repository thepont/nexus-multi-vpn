## 2025-05-15 - Hardcoded Credentials in Instrumentation Tests
**Vulnerability:** Real NordVPN service credentials were found hardcoded in `RealUserCanDoTest.kt`.
**Learning:** Hardcoded secrets in test files are a critical security risk as they are often committed to version control and can be leaked if the repository is shared or compromised.
**Prevention:** Use `InstrumentationRegistry.getArguments()` to retrieve sensitive configuration values from environment variables or CI secrets at runtime.
