# Sentinel Security Journal 🛡️

## 2026-04-08 - Information Exposure via Stack Trace Leakage
**Vulnerability:** The `VpnError.fromException` method used `e.stackTraceToString()` to populate the `details` field of the `VpnError` data class. This field is subsequently exposed to the UI layer and displayed to users when a VPN error occurs (e.g., in `getUserMessage()`).
**Learning:** Automatically including full stack traces in error objects that reach the UI layer is a common source of Information Exposure (CWE-209). This can leak internal implementation details, library versions, and file paths to end-users or potential attackers.
**Prevention:** Always sanitize exception data before sending it to the UI. Log full stack traces for developers (e.g., to Logcat or a secure backend logging service) but provide only user-friendly, non-sensitive messages to the frontend.
