## 2025-05-15 - Hardened Network Security and Secure GeoIP Migration
**Vulnerability:** Cleartext traffic (HTTP) used for GeoIP lookups and allowed globally in manifest, plus insecure backups enabled.
**Learning:** Default Android configurations and some free GeoIP providers (like ip-api.com free tier) lack HTTPS support, leading to MitM risks and manifest security gaps. Migrating to an HTTPS-capable provider (freeipapi.com) while hardening the manifest and network security config provides a layered defense.
**Prevention:** Always use HTTPS for external API calls, disable cleartext traffic and backups in production manifests, and use debug overrides for testing. Use `@SerializedName` to maintain internal API compatibility when switching providers.
