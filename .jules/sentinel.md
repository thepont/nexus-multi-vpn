# Sentinel Security Journal 🛡️

## 2026-04-01 - Information Leakage via VpnError Stack Traces
**Vulnerability:** The `VpnError.fromException` method was using `e.stackTraceToString()` to populate the `details` field, which is subsequently shown to users in the UI.
**Learning:** While useful for developers, exposing full stack traces in production UI can leak sensitive internal implementation details, such as package structures, class names, and line numbers.
**Prevention:** Always sanitize exception data before exposing it to the UI or external layers. Use `e.toString()` instead of `e.stackTraceToString()` for a safer, high-level summary that still provides context without leaking the full call stack.
