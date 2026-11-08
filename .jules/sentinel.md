## 2026-03-01 - Prevent Information Disclosure in User-Facing VPN Errors
**Vulnerability:** `VpnError.fromException` previously invoked `e.stackTraceToString()` and stored it in the `details` field, which was then displayed directly to users via `getUserMessage()`, leaking technical runtime environment, class structure, and source file metadata (CWE-209).
**Learning:** Error conversion utilities in core components must avoid attaching raw stack traces directly to domain models displayed by UI layers or exported across service boundaries.
**Prevention:** Sanitize exception reporting by storing high-level messages (`errorMsg`) rather than raw stack traces, and enforce this via dedicated unit tests.
