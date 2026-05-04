## 2026-05-02 - [CRITICAL] Hardcoded credentials in instrumentation tests
**Vulnerability:** Hardcoded NordVPN service credentials were found in `app/src/androidTest/java/com/multiregionvpn/RealUserCanDoTest.kt`.
**Learning:** These credentials were used for UI testing but committed to the repository, potentially exposing them to anyone with access to the code.
**Prevention:** Always use `InstrumentationRegistry.getArguments()` to pass sensitive information to instrumentation tests at runtime, and never commit real credentials to version control.

## 2026-05-03 - [HIGH] Insecure GeoIP lookup and information leakage
**Vulnerability:** GeoIpService was using `http://ip-api.com/` which transmits location data in cleartext. Additionally, `VpnError` was leaking full stack traces to users.
**Learning:** Legacy diagnostic services often lag behind in security protocol adoption (HTTPS). Hardening production manifest settings can break E2E tests that rely on local unencrypted mocks.
**Prevention:** Migrate to HTTPS-enabled providers (e.g., `ipwho.is`). Use `tools:replace` in debug manifests to allow necessary testing flexibility while keeping production defaults secure. Use `e.toString()` instead of `e.stackTraceToString()` for user-facing errors.
