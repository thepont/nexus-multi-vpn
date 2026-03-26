## 2024-05-24 - Remediate Sensitive Data Exposure on Disk
**Vulnerability:** VPN credentials (username and password) were being written to temporary plaintext files in the application's cache directory (`nord_auth_*.txt`) before being passed to the OpenVPN client via the `auth-user-pass` directive.
**Learning:** Storing credentials on disk, even temporarily, creates a risk of sensitive data exposure if the files are not properly cleaned up or if other processes gain access to the cache directory. Modern VPN clients like OpenVPN 3 can accept credentials directly via memory-resident strings.
**Prevention:** Avoid writing secrets to disk. Refactor interfaces to pass credentials as strings directly through the call stack from secure storage (e.g., encrypted database or KeyStore) to the underlying engine.

## 2024-05-24 - Prevent Information Leakage in Error Details
**Vulnerability:** `VpnError.fromException` was including the full exception stack trace in the `details` field of the `VpnError` object, which was then broadcast to the UI and logged.
**Learning:** Exposing stack traces to the user interface or verbose logs can leak internal application structure, library versions, and logic flow, which can be used by an attacker to find other vulnerabilities (CWE-209).
**Prevention:** Use generic error messages for the UI and only include high-level context (like the exception's simple class name) in public-facing error details.
