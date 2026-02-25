## 2025-02-25 - Secure Error Handling and Temporary Credential Management
**Vulnerability:** Exposing full stack traces in user-facing error objects (`VpnError`) and leaving temporary plaintext credential files in the cache directory with default permissions.
**Learning:** Even if a class is intended to be secure, implementation details like `e.stackTraceToString()` can easily leak sensitive internal state to the UI. Temporary files used for native integrations (like OpenVPN 3) are often forgotten and can persist with broader-than-necessary permissions.
**Prevention:** Always use specific, sanitized error messages for UI display. Implement explicit cleanup logic and owner-only permissions for any temporary files containing credentials, even in "private" app directories.
