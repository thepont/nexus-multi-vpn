# Sentinel Security Journal

## 2024-05-24 - [Insecure Network Traffic and Manifest Configuration]
**Vulnerability:** Application uses unencrypted HTTP for geolocation services and allows cleartext traffic and backups in the production manifest.
**Learning:** Legacy configurations and testing requirements often lead to insecure defaults in production manifests. Using `http-api.com` without SSL exposes user location data to MITM attacks.
**Prevention:** Always use HTTPS for external services. Separate debug and production manifest configurations to allow testing while maintaining production security.

## 2024-05-24 - [Information Leakage in Error Handling]
**Vulnerability:** `VpnError.fromException` was leaking internal stack traces via the `details` field when converting exceptions.
**Learning:** Returning full stack traces to the UI or logging them in production can expose internal implementation details, library versions, and potentially sensitive memory addresses or data, aiding an attacker in footprinting the application.
**Prevention:** Explicitly sanitize error details by only including the exception message or a high-level summary. Avoid `Throwable.stackTraceToString()` in production-facing error models.

## 2024-06-04 - [Orphaned Submodule References in CI]
**Issue:** Legacy Git submodule references (like `libs/ics-openvpn`) without corresponding `.gitmodules` entries cause CI checkout and cleanup failures (exit code 128).
**Learning:** Incomplete submodule removal leaves orphaned entries in the Git index that break automated workflows.
**Prevention:** Use `git rm --cached <path>` to explicitly remove orphaned submodule references from the index. Ensure `.gitmodules` and the actual directory are also cleaned up.

## 2024-06-04 - [UI-Test Synchronization and Enum Unification]
**Issue:** Fragmented `VpnStatus` enums and inconsistent UI state labels (CONNECTED vs. PROTECTED) caused Maestro E2E test failures and potential UI logic bugs.
**Learning:** Ensuring a single source of truth for critical application states is essential for both security logic and automated testing reliability.
**Prevention:** Centralize core state enums in shared modules. Include display metadata (like `displayText`) within the enum to ensure UI and test assertions stay synchronized.
