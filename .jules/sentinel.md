# Sentinel Security Journal

## 2025-05-14 - Information Exposure through Stack Traces
**Vulnerability:** The `VpnError.fromException()` method used `e.stackTraceToString()` to populate its `details` field, which was then displayed to the user in the UI. This leaked internal package structures, library versions, and execution flow.
**Learning:** Even convenience methods for debugging can become security risks if they propagate sensitive internal data to user-facing components. In Android, `VpnService` and native JNI interactions can provide particularly sensitive stack traces.
**Prevention:** Use `e.javaClass.simpleName` or a generic error code for user-facing details. Reserved full stack traces for secure, internal logging only.
