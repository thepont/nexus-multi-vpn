
## 2025-05-15 - [GeoIP HTTPS Migration & Error Hardening]
**Vulnerability:** The application used insecure HTTP (ip-api.com) for geographic region lookups, requiring cleartext traffic to be enabled. Additionally, VpnError.kt exposed full stack traces in getUserMessage(), leading to information leakage (CWE-209).
**Learning:** Legacy support for HTTP-only GeoIP providers often prevents hardening android:usesCleartextTraffic. Free alternatives like freeipapi.com support HTTPS and should be preferred. Stack traces should be captured for logging but never surfaced to users.
**Prevention:** Always use HTTPS for external API calls. Ensure production network security configurations default to cleartextTrafficPermitted="false". Sanitize error messages to separate technical details from user-facing strings.
