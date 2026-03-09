## 2026-03-08 - Hardening vs. E2E Automation Conflict
**Vulnerability:** Information leakage via system backups and insecure cleartext traffic.
**Learning:** Hardening production manifests by disabling backups and cleartext traffic can inadvertently break E2E testing tools (e.g., Maestro) that require cleartext loopback communication with the device agent on port 7001.
**Prevention:** Implement build-variant-specific manifest and network security configuration overrides in the `debug` source set, using `tools:replace` to allow necessary testing traffic while keeping production defaults secure.
