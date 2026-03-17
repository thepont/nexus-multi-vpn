# Sentinel Security Journal

## 2025-05-15 - [Secure temporary authentication files and restrict permissions]
**Vulnerability:** Plaintext VPN credentials (username/password) were written to temporary files in the app's cache directory without restrictive permissions and were never explicitly deleted, leading to sensitive data lingering on the device.
**Learning:** Temporary files used for inter-process communication (like passing credentials to a native VPN binary) must have their lifecycle carefully managed and their permissions restricted to the minimum necessary (owner-only) to prevent unauthorized access or data leakage.
**Prevention:** Implement owner-only permission restrictions immediately after file creation, and ensure a robust cleanup mechanism that triggers on successful connection, failure, and application initialization.

## 2026-03-17 - [Android Manifest Hardening and Debug Overrides]
**Vulnerability:** The production Android manifest was overly permissive, allowing backups and cleartext traffic by default, which increases the risk of data exposure.
**Learning:** Hardening the production manifest can break E2E testing tools (like Maestro) that rely on cleartext loopback communication. Manifest merger conflicts can occur if `tools:replace` is not used correctly with explicit attribute values in the overriding manifest.
**Prevention:** Always harden the production manifest (`allowBackup="false"`, `usesCleartextTraffic="false"`). Use a dedicated debug manifest (`app/src/debug/AndroidManifest.xml`) with `tools:replace` and explicit values to restore necessary testing capabilities only in non-production builds.
