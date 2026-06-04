## 2025-05-15 - Production Manifest Hardening with Debug Overrides
**Vulnerability:** Cleartext traffic allowed by default and sensitive data exposed via ADB backup.
**Learning:** Hardening the production manifest (`android:allowBackup="false"`, `android:usesCleartextTraffic="false"`) can break debug tools and local E2E tests that rely on unencrypted traffic (e.g., local mock servers).
**Prevention:** Use the debug source set (`app/src/debug/AndroidManifest.xml`) to explicitly override production security constraints using `tools:replace="android:allowBackup,android:usesCleartextTraffic"`. This maintains a high security posture for users while preserving developer velocity and testability.
