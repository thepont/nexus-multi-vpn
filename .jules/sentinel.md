# Sentinel Security Journal

## 2026-04-25 - Information Leakage via Stack Traces
**Vulnerability:** `VpnError.fromException` was using `e.stackTraceToString()` to populate the `details` field, which is subsequently shown to the user in the UI via `getUserMessage()`.
**Learning:** Providing full stack traces in production UI can leak internal implementation details, such as library versions, class names, and execution flow, aiding potential reverse engineering or exploitation.
**Prevention:** Use `e.toString()` or custom error messages for user-facing details, and keep full stack traces only for internal logging/crash reporting.

## 2026-04-25 - Insecure Transmission for GeoIP Data
**Vulnerability:** `GeoIpService` was using `http://ip-api.com/` (plaintext HTTP) for location detection.
**Learning:** For a VPN app, unencrypted location data can be tampered with via MITM attacks, potentially spoofing the user's location or indicating a false VPN status.
**Prevention:** Always use HTTPS for external API calls. Switched to `https://ipwho.is/` as it supports HTTPS for its free tier.
