# Sentinel Security Journal

## 2025-05-15 - [Secure temporary authentication files and restrict permissions]
**Vulnerability:** Plaintext VPN credentials (username/password) were written to temporary files in the app's cache directory without restrictive permissions and were never explicitly deleted, leading to sensitive data lingering on the device.
**Learning:** Temporary files used for inter-process communication (like passing credentials to a native VPN binary) must have their lifecycle carefully managed and their permissions restricted to the minimum necessary (owner-only) to prevent unauthorized access or data leakage.
**Prevention:** Implement owner-only permission restrictions immediately after file creation, and ensure a robust cleanup mechanism that triggers on successful connection, failure, and application initialization.
