## 2026-04-25 - Insecure Credential Storage in Cache
**Vulnerability:** VPN credentials (username/password) were being written to temporary plaintext files in the application's cache directory to be read by the OpenVPN client. These files were not explicitly deleted after use, posing a risk of sensitive data exposure on rooted devices or via forensic analysis.
**Learning:** Even though the files were stored in the app's private `cacheDir`, any persistence of plaintext secrets on disk is a security risk. The underlying OpenVPN 3 C++ wrapper already supported in-memory credential passing via `provide_creds()`, making the file-based bridge redundant.
**Prevention:** Always prioritize in-memory passing of sensitive data between components. Avoid using the file system as a temporary communication channel for secrets. Ensure that any necessary temporary sensitive data is explicitly wiped or use secure OS-level primitives for secret management.

## 2026-04-26 - Android Manifest Merger Failures in CI
**Vulnerability:** Inconsistent manifest attributes and missing `tools:replace` caused build failures when overriding production security settings (like `usesCleartextTraffic`) in debug builds for E2E testing.
**Learning:** When using `tools:replace` in a library or main manifest, the merging manifest (e.g., debug) MUST provide actual values for all attributes listed in `tools:replace`. Additionally, both manifests should ideally list the same set of attributes in `tools:replace` to ensure a predictable merge outcome across all build variants.
**Prevention:** Maintain strict consistency between production and debug manifest overrides. Always verify manifest merging locally with `./gradlew processDebugManifest` before pushing changes that alter application-level attributes.
