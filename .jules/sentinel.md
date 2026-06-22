## 2024-06-22 - [Information Leakage in VPN Error Messages]
**Vulnerability:** The `VpnError.getUserMessage()` function included stack trace details (via the `details` field) in the user-facing error message when an error was created using `VpnError.fromException()`.
**Learning:** Automatically including exception details in UI messages can leak sensitive internal implementation details, such as file paths, class names, and library versions (CWE-209).
**Prevention:** Always separate user-facing error summaries from internal debugging details (like stack traces). UI messages should only contain safe, actionable information for the end user.
