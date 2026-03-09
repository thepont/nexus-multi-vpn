## 2025-05-15 - [Information Leakage in VPN Errors]
**Vulnerability:** `VpnError.fromException()` was using `e.stackTraceToString()` to populate the `details` field, which is displayed to the user in the UI.
**Learning:** VPN applications often handle sensitive network-level errors. Exposing internal stack traces to the user interface provides attackers with implementation details and potential attack vectors.
**Prevention:** Sanitize all exception data before exposing it to the UI. Use `e.message` or a generic error code instead of full stack traces.

## 2025-05-15 - [Insecure Default Manifest Settings]
**Vulnerability:** The production `AndroidManifest.xml` had `android:allowBackup="true"` and `android:usesCleartextTraffic="true"` enabled.
**Learning:** Default settings in the main manifest are inherited by all build variants. For a security-critical app like a VPN, these defaults must be as restrictive as possible.
**Prevention:** Set `android:allowBackup="false"` and `android:usesCleartextTraffic="false"` in the `main` manifest. Use the `debug` manifest with `tools:replace` to provide developer-friendly overrides for local testing.
