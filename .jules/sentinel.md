## 2026-05-02 - [CRITICAL] Hardcoded credentials in instrumentation tests
**Vulnerability:** Hardcoded NordVPN service credentials were found in `app/src/androidTest/java/com/multiregionvpn/RealUserCanDoTest.kt`.
**Learning:** These credentials were used for UI testing but committed to the repository, potentially exposing them to anyone with access to the code.
**Prevention:** Always use `InstrumentationRegistry.getArguments()` to pass sensitive information to instrumentation tests at runtime, and never commit real credentials to version control.
