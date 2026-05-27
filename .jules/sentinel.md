## 2025-01-24 - Hardcoded Credentials in Test Source
**Vulnerability:** Hardcoded NordVPN Service Credentials (username/password) were found in `RealUserCanDoTest.kt`.
**Learning:** Credentials were left in instrumentation tests for convenience, likely to ensure they were available during E2E test development.
**Prevention:** Always use `InstrumentationRegistry.getArguments()` for sensitive data in Android tests. Configure `build.gradle.kts` to pull these values from environment variables or encrypted secrets in CI/CD pipelines.
