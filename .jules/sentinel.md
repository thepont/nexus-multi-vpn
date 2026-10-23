## 2025-05-18 - Restricting File Permissions on Temporary Auth Files
**Vulnerability:** Temporary files created on shared storage (like application cache directory) holding OpenVPN credentials in plaintext could be accessible to other processes/users if default file permissions allow world/group reading.
**Learning:** Permission setters (`File.setReadable()`, `File.setWritable()`, `File.setExecutable()`) in Java/Kotlin must be called after the file is created on disk (e.g. after `writeText()`), otherwise `File.setReadable(true, true)` returns false and does not affect the created file.
**Prevention:** Always invoke permission constraints immediately after creating temporary file contents and verify owner-only access flags.
