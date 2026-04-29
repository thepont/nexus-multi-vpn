## 2026-04-28 - [HTTPS Migration and Manifest Hardening]
**Vulnerability:** Use of insecure HTTP for sensitive GeoIP lookups and overly permissive Android manifest settings.
**Learning:** `ip-api.com` does not support HTTPS on its free tier, requiring a move to `ipwho.is` to secure user location data. Additionally, `android:usesCleartextTraffic` and `android:allowBackup` were enabled by default, increasing the attack surface.
**Prevention:** Always use HTTPS for external API calls. Enforce strict `AndroidManifest` security attributes (`allowBackup="false"`, `usesCleartextTraffic="false"`) in production builds.
