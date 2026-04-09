## 2025-05-22 - Information Exposure of Sensitive Data
**Vulnerability:** Generation of Error Message Containing Sensitive Information (CWE-209) and Insertion of Sensitive Information into Log File (CWE-532).
**Learning:** The application was leaking full stack traces to the UI via `VpnError` and logging VPN credential lengths in multiple layers (Kotlin and C++). Additionally, temporary authentication files were not explicitly marked for deletion.
**Prevention:** Sanitize error details by removing stack traces, remove all logging of credential metadata, and ensure temporary files are marked for deletion on exit.
