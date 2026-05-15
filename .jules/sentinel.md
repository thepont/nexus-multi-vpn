## 2025-05-15 - Prevent Stack Trace Leakage in VPN Errors
**Vulnerability:** Information leakage via verbose error messages. `VpnError.fromException` was using `e.stackTraceToString()` to populate the `details` field, which was then displayed to the user in the UI.
**Learning:** VPN applications often handle complex, nested exceptions (e.g., from OpenVPN or JNI). Developers might inadvertently use full stack traces for debugging purposes in production, exposing internal package names, class structures, and potentially sensitive environment information.
**Prevention:** Always use `e.toString()` or custom user-friendly error mapping in production error classes. Reserve stack traces for internal logging or debug-only builds.
