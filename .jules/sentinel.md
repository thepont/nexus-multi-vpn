## 2026-05-21 - [CRITICAL] Hardcoded Credentials in Instrumentation Tests
**Vulnerability:** Real NordVPN service credentials were found hardcoded in the `RealUserCanDoTest.kt` file.
**Learning:** During test development, developers might hardcode real credentials to quickly verify functionality, but these can be accidentally committed to the repository if not properly handled via secure injection methods.
**Prevention:** Always use `InstrumentationRegistry.getArguments()` or `BuildConfig` fields to inject sensitive test data from environment variables or secure vault stores during CI/CD execution.
