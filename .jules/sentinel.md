# Sentinel's Security Journal

## 2024-05-15 - [VpnError Stack Trace Leakage]
**Vulnerability:** The `VpnError.fromException` method was using `e.stackTraceToString()` to populate the `details` field, which is subsequently shown to users in the UI via `getUserMessage()`.
**Learning:** Using convenience methods like `stackTraceToString()` can inadvertently expose sensitive internal application structure, library versions, and potential exploit vectors to end-users.
**Prevention:** Always sanitize exception data before passing it to the UI layer. Use `e.toString()` or a custom mapper to provide only high-level information.

## 2024-05-15 - [Insecure Geo-IP Lookup]
**Vulnerability:** `GeoIpService` was using the insecure `http://ip-api.com/` endpoint for geographic location detection.
**Learning:** Plaintext HTTP requests for sensitive metadata like IP address and location allow for MITM attacks, data tampering, and user surveillance by network intermediaries.
**Prevention:** Enforce HTTPS for all external API communications. Use providers that offer secure endpoints even for free tiers (e.g., ipwho.is).
