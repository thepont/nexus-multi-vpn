# Sentinel Security Journal

## 2026-08-09 - Owner-Only File Permissions for Temporary OpenVPN Credentials
**Vulnerability:** Temporary plaintext credentials files containing sensitive VPN usernames and passwords were created in the application's cache directory without restricting standard file system permissions. On multi-user systems, shared devices, or compromised execution contexts, other local processes/applications might be able to read these files before they are deleted, leading to local privilege escalation and credential theft.
**Learning:** By default, files created in the app's cache directory using Kotlin/Java standard library `File` operations might inherit default permissive system umask/permissions unless explicitly restricted. Even if files are deleted quickly, there remains an insecure window where plaintext secrets are readable on-disk.
**Prevention:** Always restrict file system permissions to owner-only immediately upon creation for any temporary files storing sensitive data or plaintext credentials. Use `setReadable(true, true)` and `setWritable(true, true)` to explicitly restrict read/write access to the application owner process alone.
