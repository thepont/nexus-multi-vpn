## 2024-05-24 - Information Leakage via Stack Traces and Metadata
**Vulnerability:** Application was leaking full stack traces in VPN error objects and logging credential lengths/byte sizes during the OpenVPN connection process.
**Learning:** Even if actual secrets aren't logged, metadata like length or detailed internal stack traces can be used by an attacker to map the system's architecture or perform side-channel analysis.
**Prevention:** Always sanitize exception data before surfacing it to the UI or logs. Explicitly use `e.message` instead of `e.stackTraceToString()`, and avoid logging any properties derived from sensitive credentials.
