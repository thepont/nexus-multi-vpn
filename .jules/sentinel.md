## 2025-05-15 - Multi-layered Security Hardening
**Vulnerability:**
1. Hardcoded NordVPN credentials in `RealUserCanDoTest.kt`.
2. Insecure HTTP GeoIP lookup in `GeoIpService.kt` (MITM risk).
3. Stack trace disclosure in `VpnError.kt` (CWE-209).
4. Permissive production manifest (backups and cleartext enabled).

**Learning:**
A secure production configuration often conflicts with E2E testing tools (like Maestro) which may require cleartext traffic for their drivers. Using `src/debug/AndroidManifest.xml` and `src/debug/res/xml/network_security_config.xml` to provide targeted overrides is the cleanest way to maintain production security while enabling testability.

**Prevention:**
- Use instrumentation arguments for test secrets.
- Always use HTTPS for external services.
- Sanitize error messages displayed to users.
- Enforce strict security flags in the main manifest and override them explicitly in debug sourcesets if needed.
