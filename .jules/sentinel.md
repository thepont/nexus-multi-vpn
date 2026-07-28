## 2025-11-17 - XML Comments Inside Manifest Tag Merger Failures
**Vulnerability:** Security comments placed inside the `<application>` tag attribute list caused compilation parser failure.
**Learning:** XML parsers do not support putting comments within an element's start tag attribute list. Placing security annotations directly next to modified attributes (like `android:allowBackup="false"`) inside `<application ...>` causes manifest merger parsing to crash.
**Prevention:** Always place security-related XML comments outside the tag elements, preferably right before the start of the tag.
