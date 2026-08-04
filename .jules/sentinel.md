## 2025-11-17 - Prevent User-facing Stack Trace Exposure (CWE-209) in VPN Error Reporting
**Vulnerability:** Raw exception stack traces/details stored in `VpnError.details` were being directly displayed to end-users via `VpnError.getUserMessage()` in error fallback cases.
**Learning:** Displaying raw stack traces or internal environment debugging information to non-administrative users exposes implementation details, database structure, and library namespaces, facilitating targeted exploit payload generation.
**Prevention:** Hardened `getUserMessage()` to only utilize high-level localized strings and the safe `message` string. Internal exception `details` (including stack traces) remain captured for diagnostic logging but are explicitly filtered out from any client-facing user views.
