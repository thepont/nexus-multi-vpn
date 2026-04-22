# Sentinel Security Journal

## 2025-05-15 - Information Exposure in VPN Error Handling
**Vulnerability:** CWE-209: Information Exposure through an Error Message.
**Learning:** `VpnError.fromException` was using `e.stackTraceToString()` to populate the `details` field, which is subsequently displayed to the user in the UI via `getUserMessage()`. This leaks internal stack traces and implementation details (class names, line numbers, library versions) to potential attackers or inquisitive users.
**Prevention:** Always sanitize exception data before exposing it to the UI. Use `e.message` for high-level details and keep full stack traces in secure, internal logs only.

## 2025-05-15 - Manifest Hardening vs E2E Testing
**Vulnerability:** Production app allowed backups and cleartext traffic.
**Learning:** Hardening `AndroidManifest.xml` (setting `allowBackup="false"` and `usesCleartextTraffic="false"`) can break E2E testing (like Maestro) which often requires loopback cleartext or specific debug overrides. Using `tools:replace` in BOTH main and debug manifests is necessary to allow debug-only overrides while maintaining production security.
**Prevention:** Always provide actual values for all attributes listed in `tools:replace` in the debug manifest to avoid merger errors. Ensure debug network configs explicitly allow necessary test traffic (e.g., 127.0.0.1 for Maestro).
