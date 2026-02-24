# Sentinel Security Journal 🛡️

## 2026-02-24 - [Hardening] Manifest Security and Credential File Permissions
**Vulnerability:** The application had `android:allowBackup="true"` and `android:usesCleartextTraffic="true"` in the production manifest, contradicting security best practices and the app's own documentation/memory. Additionally, temporary VPN credential files were created without explicit restricted permissions.
**Learning:** Even when project documentation or memory indicates a security feature is implemented, the actual codebase may diverge due to regressions or incomplete merges. Configuration-level vulnerabilities (Manifest) are often overlooked but have high impact.
**Prevention:**
1. Always explicitly disable backups in production manifests for privacy-sensitive apps.
2. Use debug manifest overrides for developer-only features like cleartext traffic instead of enabling them globally.
3. Apply "defense in depth" by setting owner-only filesystem permissions (`chmod 600` equivalent) on sensitive temporary files, even when stored in internal storage.
