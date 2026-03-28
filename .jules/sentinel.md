## 2026-03-28 - [GeoIP Service Transition to HTTPS]
**Vulnerability:** Use of plain HTTP (`http://ip-api.com/`) for GeoIP detection, which allows for MITM attacks and tampering with geographic location data used for auto-routing.
**Learning:** Many free GeoIP services (like ip-api.com) restrict HTTPS to paid tiers, leading developers to use insecure HTTP and bypass Android's network security configuration.
**Prevention:** Always prioritize HTTPS-enabled services even for "public" data, as authenticity is as important as confidentiality. `ipwho.is` provides a viable free HTTPS alternative for this use case.
