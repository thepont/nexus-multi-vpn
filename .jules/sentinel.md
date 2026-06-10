## 2025-05-15 - Redact Stack Traces in User Messages
**Vulnerability:** Technical stack traces (CWE-209) were being leaked to the user through the `getUserMessage()` function in `VpnError.kt`.
**Learning:** The `details` field, which contained the stack trace when an error was created via `fromException()`, was being directly included in the user-facing message.
**Prevention:** Always separate developer-facing technical details from user-facing messages. Technical logs should be used for debugging, while users should only see high-level, actionable information.

## 2025-05-15 - Migrate GeoIP Service to HTTPS
**Vulnerability:** User geolocation data was being transmitted over insecure HTTP using `ip-api.com`.
**Learning:** Cleartext traffic was enabled in the manifest specifically to support this service's free tier, which does not support HTTPS.
**Prevention:** Never use HTTP for transmitting potentially sensitive user data (like location). Migrate to providers that support HTTPS (e.g., `ipwho.is`) and disable cleartext traffic globally in the production manifest using `android:usesCleartextTraffic="false"`.
