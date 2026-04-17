## 2026-04-17 - Fixed Hardcoded Credentials in Instrumentation Tests
**Vulnerability:** Hardcoded NordVPN service credentials (CWE-798) were discovered in `app/src/androidTest/java/com/multiregionvpn/RealUserCanDoTest.kt`.
**Learning:** Even in test code, hardcoding real production-like credentials is a risk as they can be committed to version control.
**Prevention:** Use `InstrumentationRegistry.getArguments()` to fetch sensitive data from the environment or command-line arguments at runtime.

## 2026-04-17 - Observation of VpnError Regression
**Vulnerability:** `VpnError.kt` was found to be leaking stack traces (CWE-209) in the `details` field, despite previous mitigation reports.
**Learning:** Security fixes can regress if not properly guarded by regression tests or if the logic is overwritten during refactoring.
**Prevention:** Always verify security properties with dedicated unit tests (e.g., `VpnErrorTest.kt`) that specifically check for the absence of sensitive data like stack traces.
