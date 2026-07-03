# Sentinel Security Journal

## 2026-07-03 - [Insecure GeoIP & Info Leakage]
**Vulnerability:** The app used insecure HTTP for GeoIP lookups and leaked stack traces in user-facing error messages.
**Learning:** `ip-api.com` does not support HTTPS for its free tier, which necessitated a provider change to `freeipapi.com` to enforce global cleartext traffic restrictions. Additionally, using `details ?: message` in UI logic directly exposed internal stack traces (CWE-209).
**Prevention:** Always prioritize HTTPS-capable APIs for sensitive metadata like location. Implement a strict "user-message vs technical-details" separation in error models, ensuring UI components only ever access the high-level message.
