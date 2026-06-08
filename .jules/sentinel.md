## 2025-05-15 - [GeoIP HTTPS and Error Leakage]
**Vulnerability:** Insecure geographic lookup via HTTP (ip-api.com) and stack trace exposure in user-facing VPN error messages.
**Learning:** `ip-api.com` requires a paid plan for HTTPS, which often leads developers to allow cleartext traffic in `network_security_config.xml`. Additionally, `VpnError.kt` was appending full stack traces to user messages, violating CWE-209.
**Prevention:** Always use HTTPS-capable providers for network services. Redact detailed internal errors (stack traces) from end-user UI, keeping them only for internal logs.
