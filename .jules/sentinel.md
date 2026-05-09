## 2025-05-15 - Prevent internal stack trace leakage in VpnError
**Vulnerability:** Information Disclosure via Exception Stack Traces.
**Learning:** `e.stackTraceToString()` in Kotlin captures the entire call stack, which can expose internal class names, method structures, and library versions to the user if displayed in the UI.
**Prevention:** Use `e.toString()` instead of `e.stackTraceToString()` when capturing exception details for user-facing error messages, ensuring only the exception type and message are exposed.

## 2025-05-15 - Secure GeoIP Migration with HTTPS
**Vulnerability:** Man-in-the-Middle (MITM) via Insecure HTTP GeoIP Lookups.
**Learning:** Using `http://ip-api.com/` exposes the application to MITM attacks where an attacker can spoof geographic data.
**Prevention:** Migrate all external API lookups to HTTPS-enabled providers like `https://ipwho.is/` and enforce secure communication by disabling `android:usesCleartextTraffic` in the manifest.
