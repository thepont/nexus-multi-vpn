## 2025-01-24 - Network Security Hardening & Information Leakage Prevention

**Vulnerability:**
1. Insecure geographic region detection via `http://ip-api.com/` (Cleartext HTTP).
2. Permissive global cleartext traffic and backup policies in `AndroidManifest.xml`.
3. Information leakage via `VpnError` exposing full stack traces in the UI.

**Learning:**
- Android's `networkSecurityConfig` allows for powerful per-domain overrides, but a global "disable cleartext" policy in production (`main`) combined with a permissive override in `debug` is the most robust way to support local E2E testing while maintaining production security.
- The `ipwho.is` API provides a secure alternative to `ip-api.com` but requires careful mapping of snake_case fields (e.g., `country_code`) using `@SerializedName` and does not support the `/json` path suffix for current IP lookups.
- Updating AGP from `8.2.0` to `8.2.1` is necessary when using Java 21 in the development environment to avoid `JdkImageTransform` failures during compilation.

**Prevention:**
- Always use HTTPS for external APIs, especially those handling location or device data.
- Enforce `android:allowBackup="false"` and `android:usesCleartextTraffic="false"` in the production manifest.
- Mask internal exceptions using `e.toString()` instead of `e.stackTraceToString()` before displaying them to users.
