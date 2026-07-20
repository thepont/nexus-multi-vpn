# Sentinel Security Journal

## 2026-03-02 - Insecure Plaintext Credential Temporary Storage
**Vulnerability:** Plaintext VPN credentials (username and password) were written to a temporary auth file in the application's cache directory during tunnel preparation but were never cleaned up. This left sensitive, unencrypted credentials lingering indefinitely on the filesystem.
**Learning:** OpenVPN 3 C++ library requires credentials to be supplied, and a common way to achieve this on Android is writing them to a temporary file for the library's file reader. However, if the application does not explicitly delete this file, the credentials linger in the internal storage cache directory, risking exposure to backup extraction, local file read vulnerabilities, or compromised root environments.
**Prevention:** Always delete temporary credentials files immediately after their contents are read into JVM memory or passed to the native layer. Wrapping the file reading logic in a `try-finally` block guarantees that the sensitive file is securely deleted from disk, even if execution throws an exception or returns early.
