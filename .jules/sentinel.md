## 2025-05-15 - Prevent Stack Trace Leakage in VPN Errors
**Vulnerability:** Information leakage via verbose error messages. `VpnError.fromException` was using `e.stackTraceToString()` to populate the `details` field, which was then displayed to the user in the UI.
**Learning:** VPN applications often handle complex, nested exceptions (e.g., from OpenVPN or JNI). Developers might inadvertently use full stack traces for debugging purposes in production, exposing internal package names, class structures, and potentially sensitive environment information.
**Prevention:** Always use `e.toString()` or custom user-friendly error mapping in production error classes. Reserve stack traces for internal logging or debug-only builds.

## 2025-05-16 - Manifest Hardening with Dual-Manifest Pattern
**Vulnerability:** Insecure application configuration in production manifest (`allowBackup="true"`, `usesCleartextTraffic="true"`). This could allow unauthorized data access via ADB backups and expose traffic to MITM attacks.
**Learning:** Production-ready applications must explicitly disable insecure features like backups and cleartext traffic. However, these features are often needed for E2E diagnostics and local HTTP mocks.
**Prevention:** Use a dual-manifest pattern where `app/src/main/AndroidManifest.xml` enforces hardened settings, and `app/src/debug/AndroidManifest.xml` uses `tools:replace` to selectively re-enable features for testing.

## 2025-05-16 - Critical UI State Inconsistency affecting E2E Tests
**Vulnerability:** Broken visibility assertions in CI/CD due to terminology mismatch ('CONNECTED' vs 'PROTECTED') across different modules and test scripts.
**Learning:** When core domain states (like VPN status) are duplicated or inconsistently named across the application, automated E2E tests (like Maestro) that rely on regex-based UI inspection will fail.
**Prevention:** Centralize all domain states in a single 'shared' source of truth and ensure UI components strictly use the defined terminology. Standardize on 'PROTECTED' for active VPN states to match both technical and user-facing requirements.
