## 2025-05-15 - Information Exposure in VpnError
**Vulnerability:** CWE-209: Information Exposure Through an Error Message. The `VpnError.fromException` method was using `Throwable.stackTraceToString()` to populate the `details` field, which was then broadcast and potentially displayed in the UI.
**Learning:** Error handling paths often default to providing as much information as possible for debugging, but in production, this can leak sensitive implementation details.
**Prevention:** Always sanitize exception data before exposing it to the UI or broadcasting it. Use only the exception message or a generic error message for user-facing details.
