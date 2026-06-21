## 2025-05-14 - Secure GeoIP Migration and HTTPS Enforcement
**Vulnerability:** Insecure geographic region lookup via HTTP (ip-api.com) and permissive network security configuration allowing cleartext traffic for that domain.
**Learning:** free tier of ip-api.com explicitly blocks HTTPS requests (403 Forbidden). Migrating to freeipapi.com provides a compatible free tier that supports HTTPS.
**Prevention:** Always enforce HTTPS in network_security_config.xml and use @SerializedName for API migrations to maintain backward compatibility with existing data models.
