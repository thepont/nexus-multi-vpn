## 2025-05-18 - Input Sanity Checks in ProcNetParser
**Vulnerability:** Unsanitized IP address strings and port numbers passed to `ipToHex()` and `portToLittleEndianHex()` could cause uncaught `NumberFormatException` or `IndexOutOfBoundsException` crashes during low-level packet routing lookup.
**Learning:** System utility functions parsing raw network packets or network state files (/proc/net) must treat all input strings as potentially malformed.
**Prevention:** Always perform bounds checking (`toIntOrNull()` and range assertions) before performing byte arithmetic or format conversions.
