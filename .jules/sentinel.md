# Sentinel's Journal - Critical Security Learnings

## 2025-11-07 - Manifest Merger Conflicts during Hardening
**Vulnerability:** Application failed to initialize correctly after hardening the production manifest with `android:allowBackup="false"` and `android:usesCleartextTraffic="false"`.
**Learning:** The production manifest used `tools:replace="android:theme,android:name,android:label"`. When the debug manifest attempted to override security attributes without also explicitly defining the replaced attributes, the manifest merger failed or resulted in inconsistent state.
**Prevention:** When hardening a production manifest that already uses `tools:replace`, the debug manifest must also explicitly define those same attributes (e.g., `android:name`, `android:theme`) in its own `application` tag to ensure a clean merge and proper Hilt/Application initialization.

## 2025-11-07 - GeoIP Migration for HTTPS Support
**Vulnerability:** `ip-api.com` requires a paid plan for HTTPS, leading to insecure network transmission of user location data in the free tier.
**Learning:** `ipwho.is` provides a comparable API with free HTTPS support and a simpler JSON structure.
**Prevention:** Always prioritize geolocation services that offer HTTPS in their free tier (like `ipwho.is`) to avoid needing cleartext exceptions in `network_security_config.xml`.
