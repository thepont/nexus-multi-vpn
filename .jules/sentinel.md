## 2024-05-22 - [Insecure GeoIP Lookup via HTTP]
**Vulnerability:** GeoIpService used 'http://ip-api.com/' and the app had 'android:usesCleartextTraffic="true"', allowing MITM attacks (CWE-319).
**Learning:** VPN apps must never use cleartext traffic for secondary services, as it compromises the anonymity and security they are built to provide.
**Prevention:** Enforce HTTPS globally via 'android:usesCleartextTraffic="false"' and 'network_security_config.xml'.

## 2024-05-22 - [Information Disclosure in VpnError]
**Vulnerability:** Full stack traces were leaked to the UI in the 'details' field (CWE-209).
**Learning:** Error details shown to users must be sanitized to prevent disclosure of implementation details.
**Prevention:** Only include exception messages in user-facing error fields.
