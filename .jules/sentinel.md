## 2025-05-15 - [Insecure GeoIP Lookup and Cleartext Traffic Exposure]
**Vulnerability:** The application was performing GeoIP lookups over insecure HTTP (ip-api.com) and had `android:usesCleartextTraffic="true"` enabled in the production manifest.
**Learning:** Legacy configurations often leave "temporary" cleartext exceptions for testing that persist into production, undermining the security of sensitive user data like location.
**Prevention:** Enforce HTTPS-only traffic globally in the main manifest and use secure, TLS-enabled geolocation providers. Ensure any debug-specific cleartext exceptions are strictly isolated to the debug manifest.
