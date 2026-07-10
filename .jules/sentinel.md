## 2024-07-10 - Network Hardening and GeoIP Privacy
**Vulnerability:** Unencrypted transmission of user location data via `ip-api.com` (HTTP) and insecure application defaults (`allowBackup="true"`, `usesCleartextTraffic="true"`).
**Learning:** The free tier of `ip-api.com` does not support HTTPS, which led to disabling global network security features to accommodate it.
**Prevention:** Always prioritize providers that support HTTPS for sensitive data like IP/Location. Enforce `cleartextTrafficPermitted="false"` in `network_security_config.xml` and use `android:allowBackup="false"` for production VPN apps to prevent data extraction.

## 2024-07-10 - Information Leakage in VPN Errors
**Vulnerability:** CWE-209: Exposure of Sensitive Information Through an Error Message. Stack traces were being included in `VpnError.getUserMessage()`.
**Learning:** Developers often include `details` or `stackTraceToString()` in error objects for debugging but forget to strip them before showing to the user.
**Prevention:** Separate internal error details from user-facing messages. Always verify that `getUserMessage()` or equivalent methods only return non-sensitive, actionable information.
