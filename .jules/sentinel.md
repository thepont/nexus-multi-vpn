## 2026-04-25 - Information Leakage & Insecure Communication

**Vulnerability:**
1. `VpnError.kt` was leaking full internal stack traces to the UI via `e.stackTraceToString()`, exposing implementation details and potentially sensitive context to users.
2. `GeoIpService.kt` and `IpCheckService.kt` were using insecure `http://ip-api.com/` for geolocation, making the app vulnerable to MITM attacks.
3. `AndroidManifest.xml` and `network_security_config.xml` explicitly permitted cleartext traffic, increasing the risk of insecure communications.

**Learning:**
1. Standard exception handling often defaults to verbose stack traces which are helpful for developers but a security risk if exposed to end-users or logs.
2. Legacy integrations sometimes persist with insecure protocols (HTTP) even when secure alternatives (HTTPS) are available.
3. Overly permissive network security configurations (allowing cleartext) can negate the benefits of other security measures.

**Prevention:**
1. Always sanitize error messages intended for UI or external consumption. Use `e.toString()` or custom error mapping instead of full stack traces.
2. Enforce HTTPS for all external API calls.
3. Maintain a strict `network_security_config.xml` that disables cleartext traffic by default and only allows it for specific, well-justified domains (e.g., localhost during testing).
