# Sentinel's Security Journal

Welcome to Sentinel's Security Journal for the Multi-Region VPN Router project.

This journal is used to document critical security findings, reusable patterns, and architectural gaps discovered during our security audits.

## 2024-08-01 - Plaintext VPN Credentials Lingering and Insecure File Permissions
**Vulnerability:** Plaintext VPN credentials were temporarily written to standard world-readable/group-readable files in cacheDir, and were not immediately deleted after being loaded into the native client memory, allowing them to linger indefinitely on disk.
**Learning:** Creating temporary on-disk handshake/auth files can introduce insecure storage and privilege escalation risks on multi-user systems. It is critical to enforce the principle of least privilege on both file permissions and the lifetime of the sensitive resource.
**Prevention:** Always set owner-only permissions (`setReadable(true, true)` and `setWritable(true, true)`) on credential/sensitive files immediately upon creation, and delete them immediately using `try-finally` blocks as soon as their contents are read into RAM.
