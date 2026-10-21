## 2025-05-20 - Restrictive Permissions on Temporary Credentials Files
**Vulnerability:** Temporary authentication files containing plaintext credentials created in the app cache directory had default world/group readable permissions.
**Learning:** `File.setReadable()`, `File.setWritable()`, and `File.setExecutable()` must be called after the file is created on disk (e.g. after `writeText()`), as calling permission setters on non-existent `File` objects has no effect.
**Prevention:** Explicitly restrict file permissions (`setReadable(true, true)`, `setWritable(true, true)`, `setExecutable(false, false)`) immediately after writing credential files.
