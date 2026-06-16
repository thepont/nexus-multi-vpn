## 2026-06-16 - [Information Disclosure Prevention]
**Vulnerability:** Technical error details (CWE-209), including stack traces, were being exposed to users via the UI in `VpnError.getUserMessage()`.
**Learning:** While technical details are useful for debugging, they should not be directly displayed in the user interface as they can leak internal application structure to potential attackers.
**Prevention:** Always separate user-facing error messages from internal technical logs. Use a high-level summary for the UI and keep detailed stack traces in secure logs or non-exported object fields.
