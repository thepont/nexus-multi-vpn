## 2025-05-15 - Information Exposure in VpnError
**Vulnerability:** The `VpnError.fromException` method was including the full stack trace in the `details` field, which is often exposed to the UI.
**Learning:** Raw exception data was being passed directly to domain-level error objects without sanitization, leading to CWE-209.
**Prevention:** Always sanitize exception data before including it in objects that might be exposed to the user interface or external logs.
