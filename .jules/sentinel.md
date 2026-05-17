# Sentinel's Security Journal

## 2024-05-16 - [Plaintext Credential File Bridge]
**Vulnerability:** VPN credentials were being written to temporary plaintext files in the application cache (`nord_auth_[id].txt`) before being passed to the OpenVPN client via the `.ovpn` configuration file's `auth-user-pass` directive.
**Learning:** This "file bridge" pattern is often used to work around API limitations but creates a significant security gap where credentials persist in storage. Even if deleted after use, they are vulnerable during the connection window or if the app crashes.
**Prevention:** Pass credentials directly through memory across all layers (Kotlin -> JNI -> C++). Ensure native layers are configured to receive credentials via API calls (like `provide_creds()`) rather than reading from disk.

## 2024-05-16 - [Hardcoded Test Credentials]
**Vulnerability:** NordVPN credentials were hardcoded in `RealUserCanDoTest.kt` for E2E testing.
**Learning:** Hardcoding credentials in tests often leads to them being committed to version control, especially in private repos that might later be made public.
**Prevention:** Use dynamic lookups from environment variables or instrumentation arguments (e.g., `InstrumentationRegistry.getArguments()`) to inject secrets at runtime in CI/CD environments.

## 2024-05-17 - [NDK License and Versioning in CI]
**Vulnerability:** CI build failures due to missing NDK licenses and version mismatches.
**Learning:** GitHub Actions runners might have multiple NDK versions installed but may not have accepted licenses for the specific version required by the build, leading to "Failed to install" errors even when the component is technically present.
**Prevention:** Explicitly set `ndkVersion` in `build.gradle.kts` and ensure CI scripts run `sdkmanager --licenses` to pre-approve all required components.
