# Sentinel Security Journal

## 2026-03-02 - Insecure Temporary Plaintext Credentials Storage
**Vulnerability:** Plaintext VPN credentials (username and password) written to a temporary auth file during tunnel preparation were created with standard file permissions. This left sensitive, unencrypted credentials temporarily readable by any local process or side-channel on the device until deleted.
**Learning:** OpenVPN 3 C++ libraries expect to read username/password from a temporary auth file. When creating this file, standard Java file creation APIs use default system permissions, which might allow group or other system users to read the file in certain configurations.
**Prevention:** Always explicitly restrict temporary credentials files to owner-only read/write access (`setReadable(true, true)` and `setWritable(true, true)`) immediately upon file creation, and delete them securely using a `try-finally` block as soon as they are no longer required.
