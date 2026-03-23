# Sentinel Security Journal

## 2026-03-22 - Secrets on Disk Vulnerability
**Vulnerability:** VPN credentials (username and password) were written to plaintext temporary files in the application's cache directory (`nord_auth_*.txt`) to be read by the OpenVPN client. These files were never deleted, leading to persistent sensitive data on the device.

**Learning:** Temporary files used for inter-component communication are often unnecessary and increase the attack surface. Even within "private" directories, sensitive data should not be persisted in plaintext if in-memory alternatives exist.

**Prevention:** Refactor data flow to pass credentials directly as strings through application layers to the consumer (e.g., JNI/Native layer). Avoid disk-based intermediate storage for secrets.
