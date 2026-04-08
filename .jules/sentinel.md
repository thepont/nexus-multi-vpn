## 2025-05-15 - [Critical] Mandatory HTTPS for GeoIP services
**Vulnerability:** The application was using HTTP for GeoIP lookups via ip-api.com.
**Learning:** In a VPN application, any unencrypted metadata related to a user's location (like GeoIP requests) is a significant privacy risk and enables MITM attacks. Even if the primary traffic is tunneled, early bootstrap lookups can leak identity.
**Prevention:** Always enforce HTTPS for any external service call and maintain a restrictive network security configuration that explicitly denies cleartext traffic for all domains.
