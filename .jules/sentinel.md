## 2024-05-24 - Secure Geo-IP Migration and Network Hardening
**Vulnerability:** Use of insecure HTTP (`http://ip-api.com/`) for geographic IP lookups, exposing sensitive user location data to MITM attacks and requiring `android:usesCleartextTraffic="true"`.
**Learning:** Many free Geo-IP services (like `ip-api.com`) only support HTTPS on paid tiers, leading developers to use insecure endpoints. `ipwho.is` provides a viable HTTPS alternative for free usage.
**Prevention:** Always prioritize HTTPS for any network request involving user-identifiable data (like IP or location). Harden `AndroidManifest.xml` by disabling cleartext traffic and backups to minimize the attack surface.
