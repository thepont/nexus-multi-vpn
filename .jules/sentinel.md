## 2025-05-14 - Hardcoded Service Credentials in E2E Tests
**Vulnerability:** Real NordVPN service credentials were found hardcoded in `RealUserCanDoTest.kt`.
**Learning:** Credentials were added to enable "Can Do" tests on real devices, but they were committed to the repository instead of being passed as external arguments.
**Prevention:** Always use `InstrumentationRegistry.getArguments()` to retrieve sensitive configuration in Android tests. Ensure build scripts (Gradle) are configured to pass these arguments from environment variables or secure local files (e.g., gitignored `.env`).

## 2025-05-14 - Manifest Merger Conflicts with Security Hardening
**Vulnerability:** Disabling backups and cleartext in the production manifest caused CI failures because E2E tests and local mock servers relied on these features.
**Learning:** Production security hardening must be reconciled with test environments. If production disables a feature that tests need, the debug manifest must explicitly override these settings AND use `tools:replace` to ensure the merge is successful.
**Prevention:** Always consolidate all `tools:replace` attributes into the debug manifest and explicitly define the overridden attributes there. Ensure a permissive `network_security_config.xml` exists for debug builds if cleartext is needed for testing.
