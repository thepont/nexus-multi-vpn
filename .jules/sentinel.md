## 2026-03-04 - [Information Leak & Manifest Hardening]
**Vulnerability:** Information leakage through stack traces in VPN error messages, and insecure default manifest settings (backups enabled, cleartext traffic permitted).
**Learning:** Generic error handling in Android often defaults to exposing full stack traces for debugging, which can leak internal implementation details to users or attackers. Manifest defaults like `android:allowBackup="true"` and `android:usesCleartextTraffic="true"` are often left as-is, increasing the attack surface.
**Prevention:** Always sanitize exception details before exposing them to the UI. Harden the `AndroidManifest.xml` by explicitly disabling backups and cleartext traffic, using `network_security_config.xml` for necessary exceptions.
## 2026-03-04 - [Maestro E2E Cleartext Traffic]
**Vulnerability:** N/A (Functional regression caused by security hardening)
**Learning:** Maestro E2E tests require cleartext traffic to communicate with the device agent over local ports. Disabling it globally in `AndroidManifest.xml` breaks these tests.
**Prevention:** Use build variants to apply strict security settings. Keep `android:usesCleartextTraffic="false"` in `main/AndroidManifest.xml` for production, but override it with `true` in `debug/AndroidManifest.xml` for testing tools.
