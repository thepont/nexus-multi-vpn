# Sentinel's Security Journal

## 2024-06-21 - Initial Security Audit and Hardening
**Vulnerability:**
1. Unencrypted Transmission of Sensitive Geolocation Data: `GeoIpService.kt` uses `http://ip-api.com/` which transmits the user's IP and geographic location over cleartext HTTP. This can be intercepted by ISPs or attackers on the same network.
2. Insecure Application Configuration: `AndroidManifest.xml` has `android:usesCleartextTraffic="true"` and `android:allowBackup="true"`. This allows unencrypted network traffic globally and enables potential credential leakage through Android backups.
3. Verbose Error Messages (CWE-209): `VpnError.kt` includes full stack traces in the `getUserMessage()` function, which is shown to users, potentially exposing internal system details.

**Learning:**
The application was using `ip-api.com`'s free tier which only supports HTTP, necessitating a cleartext exception in `network_security_config.xml`. This led to a broader security regression where cleartext traffic was permitted globally in the manifest.

**Prevention:**
Always use HTTPS for network services. Ensure manifest hardening (backup=false, cleartext=false) is standard for production-ready VPN applications. Implement structured error handling that separates internal debug details from user-facing messages.
