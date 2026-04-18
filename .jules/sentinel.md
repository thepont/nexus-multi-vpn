## 2026-04-18 - Hardened External Service Access (GeoIP)
**Vulnerability:** The app was using insecure HTTP (`http://ip-api.com/`) for GeoIP lookups, enabling MITM attacks and compromising anonymity. Cleartext traffic was also permitted globally.
**Learning:** VPN apps MUST ensure all auxiliary network traffic (GeoIP, updates, etc.) is encrypted to maintain the user's security posture.
**Prevention:** Forced HTTPS globally in `AndroidManifest.xml` (`android:usesCleartextTraffic="false"`) and migrated to `https://ipwho.is/`.

## 2026-04-18 - Fixed Hardcoded Credentials in Instrumentation Tests
**Vulnerability:** Hardcoded NordVPN service credentials (CWE-798) were discovered in `app/src/androidTest/java/com/multiregionvpn/RealUserCanDoTest.kt`.
**Learning:** Even in test code, hardcoding real production-like credentials is a risk as they can be committed to version control.
**Prevention:** Use `InstrumentationRegistry.getArguments()` to fetch sensitive data from the environment or command-line arguments at runtime.

## 2026-04-18 - Information Disclosure Mitigation in VpnError
**Vulnerability:** `VpnError.kt` was found to be leaking stack traces (CWE-209) in the `details` field.
**Learning:** Error objects shared with the UI should never contain internal implementation details like stack traces.
**Prevention:** Sanitize the `details` field in `VpnError.fromException` to only include the exception message.
