## 2026-03-09 - Owner-Only Local Credentials Storage Hardening
**Vulnerability:** Weak local file permissions on temporary files containing plaintext VPN credentials stored in the application's cache directory, potentially exposing sensitive data to other processes or local attackers.
**Learning:** In Android, creating temporary files in `context.cacheDir` can sometimes default to more permissive file creation masks depending on device configuration. Sensitive raw/plaintext files must be explicitly restricted right after creation.
**Prevention:** Always invoke `setReadable(true, true)` and `setWritable(true, true)` on any temporary files containing credentials, configurations, or certificates to enforce strict Unix 600 (owner-only) permissions.
