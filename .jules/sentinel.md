## 2025-05-18 - Prevent Stack Trace Information Disclosure in Error Objects
**Vulnerability:** `VpnError.fromException()` stored full exception stack traces in `details` via `e.stackTraceToString()`, which was then exposed directly to users via `getUserMessage()`.
**Learning:** Returning or storing full exception stack traces in UI/data objects creates CWE-209 information disclosure risks, exposing system file paths and internal class names.
**Prevention:** Store high-level exception type identifiers (e.g., `e.javaClass.simpleName`) instead of raw stack traces, and ensure user-facing messages only include safe, sanitized information.
