## 2024-11-21 - [Secure Error Handling & Secret Mitigation]
**Vulnerability:** Information Disclosure (CWE-209) via stack trace leakage in `VpnError` and Hardcoded Credentials in `RealUserCanDoTest`.
**Learning:** Stack traces were inadvertently exposed to the UI, potentially leaking internal architecture. Additionally, secrets were hardcoded in tests, a common but critical risk.
**Prevention:** Always sanitize exception details before displaying them to users. Use instrumentation arguments or environment variables to inject secrets into tests dynamically.
