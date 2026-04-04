# Sentinel Security Journal

## 2025-05-15 - Insecure GeoIP Lookup Fixed
**Vulnerability:** GeoIpService.kt used an insecure HTTP endpoint (`http://ip-api.com/`) for geographic region lookups, transmitting the user's IP address and location in plaintext. The `network_security_config.xml` also explicitly allowed cleartext traffic for this domain.
**Learning:** Transitioning to `https://ipwho.is/` provides a free HTTPS-capable replacement. This required updating data mapping (Snake_case `country_code` to CamelCase `countryCode`) using `@SerializedName` in Gson and `@Json` in Moshi to maintain codebase consistency while fixing the transport security.
**Prevention:** Always use HTTPS for external services, especially those involving user identifiers like IP addresses. Periodically audit `network_security_config.xml` for `cleartextTrafficPermitted="true"` exceptions.
