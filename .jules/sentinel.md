## 2024-11-21 - [Secure Error Handling & Secret Mitigation]
**Vulnerability:** Information Disclosure (CWE-209) via stack trace leakage in `VpnError` and Hardcoded Credentials in `RealUserCanDoTest`.
**Learning:** Stack traces were inadvertently exposed to the UI, potentially leaking internal architecture. Additionally, secrets were hardcoded in tests, a common but critical risk.
**Prevention:** Always sanitize exception details before displaying them to users. Use instrumentation arguments or environment variables to inject secrets into tests dynamically.

## 2024-11-21 - [Secure GeoIP Migration & CI Hardening]
**Vulnerability:** Insecure HTTP usage for GeoIP services and cleartext network configuration risks.
**Learning:** VPN apps should never use cleartext traffic as it compromises user anonymity. CI environments require explicit submodule metadata (.gitmodules) and debug manifest overrides (tools:replace) for automated E2E drivers.
**Prevention:** Enforce `android:usesCleartextTraffic="false"` in production. Migrated all services to `https://ipwho.is/`.
