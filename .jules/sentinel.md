## 2025-05-15 - Hardcoded Credentials in Instrumentation Tests
**Vulnerability:** Real NordVPN service credentials were found hardcoded in `RealUserCanDoTest.kt`.
**Learning:** Hardcoded secrets in test files are often overlooked but pose the same risk as secrets in production code, especially if the repository is shared or public.
**Prevention:** Use `InstrumentationRegistry.getArguments()` to pass sensitive configuration to Android instrumented tests dynamically from environment variables or CI secrets.
