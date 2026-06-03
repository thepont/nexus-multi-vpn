# Sentinel Security Journal

## 2024-05-24 - [Insecure Network Traffic and Manifest Configuration]
**Vulnerability:** Application uses unencrypted HTTP for geolocation services and allows cleartext traffic and backups in the production manifest.
**Learning:** Legacy configurations and testing requirements often lead to insecure defaults in production manifests. Using `http-api.com` without SSL exposes user location data to MITM attacks.
**Prevention:** Always use HTTPS for external services. Separate debug and production manifest configurations to allow testing while maintaining production security.

## 2024-05-24 - [Information Leakage in Error Handling]
**Vulnerability:** `VpnError` could potentially leak internal stack traces when converting exceptions.
**Learning:** Default exception stringification can expose internal logic and sensitive data.
**Prevention:** Explicitly sanitize error messages and avoid returning full stack traces to the UI or logs.
