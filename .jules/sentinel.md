## 2026-06-16 - Prevent Information Leakage in VPN Errors
**Vulnerability:** VPN error messages were exposing full stack traces to the user via the `details` field in `VpnError.kt`.
**Learning:** Providing internal technical details to users can leak information about the app's architecture and dependencies (CWE-209).
**Prevention:** Always separate internal debug information from user-facing error messages. Redact technical details in user messages while preserving them for internal logs.

## 2026-06-16 - Hardening Network Security
**Vulnerability:** `android:usesCleartextTraffic` was set to `true` in the main manifest, allowing all HTTP traffic.
**Learning:** Defaulting to allow cleartext traffic increases the risk of MitM attacks and insecure data transmission.
**Prevention:** Set `android:usesCleartextTraffic="false"` in the main manifest. To ensure Maestro and Hilt work correctly in CI, the debug manifest must explicitly override this and other application attributes using `tools:replace`.
