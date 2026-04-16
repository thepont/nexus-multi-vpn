## 2024-05-22 - [Insecure GeoIP Lookup via HTTP]
**Vulnerability:** GeoIpService used 'http://ip-api.com/' and the app had 'android:usesCleartextTraffic="true"', allowing MITM attacks (CWE-319).
**Learning:** VPN apps must never use cleartext traffic for secondary services, as it compromises the anonymity and security they are built to provide.
**Prevention:** Enforce HTTPS globally via 'android:usesCleartextTraffic="false"' and 'network_security_config.xml'.

## 2024-05-22 - [Manifest Overrides for E2E Testing]
**Vulnerability:** Hardening the production app by disabling cleartext traffic and backups can break local E2E testing (e.g., Maestro) that requires cleartext loopback.
**Learning:** Use Android's manifest merger and debug-specific manifests to override security constraints safely for testing without compromising production security.
**Prevention:** Use 'tools:replace' in debug manifest and provide a debug-only network security config.
