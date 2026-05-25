## 2025-05-14 - Hardcoded Service Credentials in E2E Tests
**Vulnerability:** Real NordVPN service credentials were found hardcoded in `RealUserCanDoTest.kt`.
**Learning:** Credentials were added to enable "Can Do" tests on real devices, but they were committed to the repository instead of being passed as external arguments.
**Prevention:** Always use `InstrumentationRegistry.getArguments()` to retrieve sensitive configuration in Android tests. Ensure build scripts (Gradle) are configured to pass these arguments from environment variables or secure local files (e.g., gitignored `.env`).
