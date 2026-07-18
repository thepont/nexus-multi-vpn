## 2023-11-20 - Hardening Android Network Traffic with Secure HTTPS and Debug Manifest Overrides
**Vulnerability:** The application originally had `usesCleartextTraffic="true"` globally enabled in production and an explicit cleartext domain exemption for `ip-api.com` in `network_security_config.xml`. Geo-location queries were performed via insecure `http://ip-api.com/`, allowing potential location leakage and active man-in-the-middle (MITM) manipulation.
**Learning:** Production applications must strictly disable cleartext traffic to defend against credential and routing leakages. However, local/E2E test environments running mock HTTP servers require cleartext capability. Hardcoding cleartext permissions in the main manifest leaves production open to cleartext attacks.
**Prevention:**
1. Move the geo-IP lookup to a secure endpoint that supports HTTPS (like `freeipapi.com`).
2. Hardcode `android:usesCleartextTraffic="false"` and `android:allowBackup="false"` in the production `AndroidManifest.xml` and fully harden `network_security_config.xml`.
3. Selectively override `android:usesCleartextTraffic="true"` and `android:allowBackup="true"` using `tools:replace` strictly inside `app/src/debug/AndroidManifest.xml` to allow developer convenience and HTTP mock server testing in local debug/test runs without compromising the release build.
