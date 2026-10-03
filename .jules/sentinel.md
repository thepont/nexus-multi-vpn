## 2026-10-03 - Restricted Temporary Authentication File Permissions
**Vulnerability:** Temporary OpenVPN authentication files containing plaintext credentials were generated in `cacheDir` without explicitly restricting file permissions.
**Learning:** In Java/Kotlin, calling `setReadable()` / `setWritable()` / `setExecutable()` on a `File` before creation has no effect; permission setters must be invoked immediately after `writeText()` creates the file on disk.
**Prevention:** Always invoke permission setters on temporary credential files right after writing content to disk to guarantee owner-only read/write access (`setReadable(true, true)`, `setWritable(true, true)`, `setExecutable(false, false)`).
