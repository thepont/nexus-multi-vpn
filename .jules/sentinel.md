## 2026-02-23 - [Insecure Manifest and Information Leak]
**Vulnerability:** The AndroidManifest.xml had `allowBackup="true"` and `usesCleartextTraffic="true"`, and `VpnError` leaked full stack traces to the UI.
**Learning:** Default Android template settings often prioritize convenience over security. Split tunneling and multi-region routing increases the surface area for credential exposure.
**Prevention:** Always disable backups and cleartext traffic by default. Sanitize error details before they reach the UI layer.
