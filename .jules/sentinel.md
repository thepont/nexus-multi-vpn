## 2026-02-27 - Information Exposure in VpnError
**Vulnerability:** Stack traces were being leaked to the user interface through the `VpnError.fromException` method, which used `e.stackTraceToString()` for the `details` field.
**Learning:** Error objects shared with the UI layer should never contain raw stack traces or internal implementation details.
**Prevention:** Use sanitized error messages for user-facing fields and keep detailed stack traces restricted to internal logs.

## 2026-02-27 - Plaintext Credential Persistence
**Vulnerability:** VPN authentication credentials were being stored in plaintext temporary files in the cache directory without explicit cleanup or restricted file permissions.
**Learning:** Temporary files containing secrets must have the most restrictive permissions possible and MUST be explicitly deleted as soon as they are no longer needed.
**Prevention:** Use `File.setReadable(true, true)` and `File.setWritable(true, true)` for owner-only access, and implement a robust cleanup mechanism in the service lifecycle.
