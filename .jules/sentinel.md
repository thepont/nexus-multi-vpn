## 2024-05-22 - [Insecure GeoIP Lookup via HTTP]
**Vulnerability:** GeoIpService used 'http://ip-api.com/' and the app had 'android:usesCleartextTraffic="true"', allowing MITM attacks (CWE-319).
**Learning:** VPN apps must never use cleartext traffic for secondary services, as it compromises the anonymity and security they are built to provide.
**Prevention:** Enforce HTTPS globally via 'android:usesCleartextTraffic="false"' and 'network_security_config.xml'.

## 2024-05-22 - [Credential Metadata Leak]
**Vulnerability:** Logging of password lengths in both Kotlin and C++ layers.
**Learning:** Even if actual credentials aren't logged, metadata like length can provide side-channel info.
**Prevention:** Avoid logging any properties of sensitive data, including lengths or encoding details.

## 2024-05-22 - [Information Exposure in VpnError]
**Vulnerability:** Full stack traces were being included in the 'details' field of VpnError and potentially shown to users (CWE-209).
**Learning:** Stack traces should be for internal logging only, never passed to UI components.
**Prevention:** Sanitize exception data before creating user-facing error objects.
