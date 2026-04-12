## 2024-04-11 - Fixed CWE-209: Information Exposure Through an Error Message
**Vulnerability:** The `VpnError` class was capturing full stack traces using `e.stackTraceToString()` and storing them in the `details` field, which was then directly displayed to users in the UI.
**Learning:** VPN applications often handle sensitive errors (auth, connection, etc.). Exposing stack traces to the UI can reveal internal library versions, class structures, and even sensitive data that might be present in local variables during an exception.
**Prevention:** Always sanitize error messages before displaying them in the UI. Use simple error messages for users and log full details/stack traces only to secure internal logging systems if necessary.
