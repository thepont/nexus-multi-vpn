## 2025-05-15 - [Credential Metadata and Stack Trace Leakage Hardening]
**Vulnerability:** Sensitive VPN credential metadata (lengths and first characters) were being logged across multiple layers (Kotlin, JNI, C++). Additionally, `VpnError.fromException` was leaking full stack traces to the UI layer via the `details` field.
**Learning:** Over-verbose debugging logs during the development of complex multi-layered (JNI) components often remain in the production path. Stack trace serialization is a common default for error handling that inadvertently exposes implementation details.
**Prevention:** Enforce strict sanitization of logs in components handling PII/Credentials. Abstract error "details" from raw exceptions before propagating them to the UI or logs visible to the user.
