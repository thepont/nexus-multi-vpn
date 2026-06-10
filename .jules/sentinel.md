## 2025-05-15 - HTTPS Migration and CWE-209 Prevention
**Vulnerability:** GeoIP lookup was performed over insecure HTTP (CWE-319), and VPN error messages leaked stack traces to users (CWE-209).
**Learning:** Hardening the production manifest by setting `usesCleartextTraffic="false"` and `allowBackup="false"` requires explicit overrides in the debug manifest to maintain E2E test compatibility with local mock servers.
**Prevention:** Always use HTTPS for external API calls and redact the `details` field from user-facing `VpnError` messages.
