## 2025-11-07 - Manifest Hardening and Error Detail Security
**Vulnerability:** Information leakage through stack traces in VPN errors and potential data leakage via ADB backups.
**Learning:** Production Android manifests should explicitly disable `allowBackup` and `usesCleartextTraffic` for defense-in-depth, especially for security-critical apps like VPNs.
**Prevention:** Use `tools:replace` in debug manifests to maintain development productivity (enabling backups/cleartext for tools) while keeping production hardened. Sanitize exception data before presenting it in the UI to avoid exposing internal paths or logic.
