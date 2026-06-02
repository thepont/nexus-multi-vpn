## 2025-01-24 - Prevention of stack trace leakage in VpnError
**Vulnerability:** `VpnError.fromException` was using `e.stackTraceToString()` to populate the `details` field, which was subsequently exposed to users in the UI via `getUserMessage()`. This leaked internal implementation details like class names, method names, and line numbers (CWE-209).
**Learning:** Exception handling logic in domain models or DTOs can inadvertently bundle debugging information that is unsuitable for user-facing error messages if not explicitly sanitized.
**Prevention:** Always sanitize exception details before including them in objects that traverse to the UI or external interfaces. Use `e.message` or `e.toString()` instead of full stack traces for user-facing context.

## 2025-01-24 - Orphaned Git Submodule reference causing CI failure
**Vulnerability:** Not a direct security vulnerability, but a CI/CD infrastructure issue. An orphaned git submodule reference (`libs/ics-openvpn`) existed in the index without a corresponding entry in `.gitmodules`.
**Learning:** Post-job cleanup in GitHub Actions can fail when `git submodule foreach` is called if the git index is inconsistent with `.gitmodules`.
**Prevention:** Ensure all submodule removals are complete by removing the index entry (`git rm --cached <path>`) and verifying `.gitmodules` is updated or removed.
