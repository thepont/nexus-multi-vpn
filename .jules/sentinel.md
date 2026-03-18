
## 2026-03-18 - Information Leakage Remediation and CI Compatibility
**Vulnerability:** Information leakage through full stack traces in `VpnError` objects exposed to the UI and logs.
**Learning:** Hardening the production manifest (e.g., `android:usesCleartextTraffic="false"`) can break E2E testing tools like Maestro that rely on cleartext loopback communication. Explicit debug manifest overrides with documented rationale are necessary to maintain both security posture and testability.
**Prevention:** Use `e.javaClass.simpleName` for public-facing error details. Document and isolate testing-required security overrides in debug-specific configurations.
