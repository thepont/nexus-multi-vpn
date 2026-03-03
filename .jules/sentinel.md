## 2026-03-03 - [Security Hardening & CI Compatibility]
**Vulnerability:** Information leakage through stack traces in UI error broadcasts and potential sensitive data exposure via backups/cleartext traffic.
**Learning:** Hardening `android:usesCleartextTraffic="false"` in the main manifest is a critical security measure, but it breaks Maestro E2E tests which rely on local HTTP communication with the device agent.
**Prevention:** Use a debug-specific `AndroidManifest.xml` and `network_security_config.xml` to allow cleartext traffic specifically for localhost/127.0.0.1 during testing, while maintaining strict production security.
>>>>>>> REPLACE
