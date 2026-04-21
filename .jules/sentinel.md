## 2025-05-15 - Information Exposure in VpnError
**Vulnerability:** The `VpnError.fromException` method was including the full stack trace in the `details` field, which is often exposed to the UI.
**Learning:** Raw exception data was being passed directly to domain-level error objects without sanitization, leading to CWE-209.
**Prevention:** Always sanitize exception data before including it in objects that might be exposed to the user interface or external logs.

## 2025-05-15 - Sensitive Metadata Leakage in Logs
**Vulnerability:** JNI and Kotlin layers were logging the byte lengths of usernames and passwords.
**Learning:** Even if the secret itself isn't logged, metadata like length can be used in side-channel attacks or provide hints to attackers.
**Prevention:** Avoid logging any properties of sensitive data, including its length or first/last characters.
