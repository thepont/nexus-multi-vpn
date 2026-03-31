# Sentinel Security Journal

## 2025-03-31 - [Credential Exposure in Plaintext Files]
**Vulnerability:** VPN credentials (username and password) were being written to plaintext temporary files in the application's cache directory (`nord_auth_[id].txt`). This exposed sensitive data to any process with read access to the internal storage.
**Learning:** Even "temporary" storage in Android's cache directory can persist secrets longer than intended and is susceptible to unauthorized access on rooted devices or via forensic analysis.
**Prevention:** Always handle sensitive authentication credentials entirely in memory. Pass secrets directly through the JNI layer to native libraries instead of using the filesystem as a transfer mechanism.
