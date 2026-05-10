## 2025-05-14 - Network Security Hardening with Debug Overrides
**Vulnerability:** Cleartext traffic allowed globally for GeoIP lookups and backup enabled for sensitive VPN data.
**Learning:** Hardening production network security and disabling backups can break E2E diagnostic tests that rely on local unencrypted mocks.
**Prevention:** Use a dual-manifest strategy: `app/src/main/AndroidManifest.xml` for hardened production settings and `app/src/debug/AndroidManifest.xml` with `tools:replace` and a debug-only `network_security_config.xml` to permit necessary test traffic.
