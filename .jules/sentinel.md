# Sentinel Security Journal

## 2024-05-24 - [Insecure Network Traffic and Manifest Configuration]
**Vulnerability:** Application uses unencrypted HTTP for geolocation services and allows cleartext traffic and backups in the production manifest.
**Learning:** Legacy configurations and testing requirements often lead to insecure defaults in production manifests. Using `http-api.com` without SSL exposes user location data to MITM attacks.
**Prevention:** Always use HTTPS for external services. Separate debug and production manifest configurations to allow testing while maintaining production security.

## 2024-05-24 - [Information Leakage in Error Handling]
**Vulnerability:** `VpnError.fromException` was leaking internal stack traces via the `details` field when converting exceptions.
**Learning:** Returning full stack traces to the UI or logging them in production can expose internal implementation details, library versions, and potentially sensitive memory addresses or data, aiding an attacker in footprinting the application.
**Prevention:** Explicitly sanitize error details by only including the exception message or a high-level summary. Avoid `Throwable.stackTraceToString()` in production-facing error models.
