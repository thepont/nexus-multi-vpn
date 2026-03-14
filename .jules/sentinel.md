## 2026-03-13 - [Information Disclosure & Production Hardening]
**Vulnerability:** Internal stack traces were being leaked to the UI via the `VpnError` class. Additionally, production builds had `allowBackup` and `usesCleartextTraffic` enabled, which are insecure for a VPN application.
**Learning:** Production manifests should always be hardened, and a separate `debug` manifest and `network_security_config` should be used to provide necessary developer/CI flexibility (e.g., cleartext for loopback Maestro testing) without compromising production security.
**Prevention:** Always use `e.javaClass.simpleName` for user-visible error details instead of `e.stackTraceToString()`. Use `tools:replace` in debug manifests to explicitly override production hardening only where needed.
