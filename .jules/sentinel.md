## 2026-06-18 - [Fix information leakage in VpnError messages]
**Vulnerability:** Information leakage (CWE-209) via exception stack traces in user-facing error messages.
**Learning:** Appending full exception details (including stack traces) to user-facing messages exposes internal class names and application structure, aiding attackers.
**Prevention:** Always sanitize error messages intended for users; keep detailed stack traces in internal logs only.
