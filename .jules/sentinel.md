## 2024-11-06 - Manifest Hardening and CI Stability
**Vulnerability:** Information Exposure (CWE-209) and Insecure Network Transmission (Cleartext).
**Learning:** Hardening the production manifest (disabling cleartext and backups) required adding a debug manifest for E2E tests. However, explicitly redefining redundant application attributes (like `android:name`) in the debug manifest caused APK installation failures ("Broken pipe") and emulator system instability in CI.
**Prevention:** Keep debug manifests minimal. Only include attributes that strictly need to be overridden (using `tools:replace`) and avoid matching the main manifest's non-overridden attributes.
