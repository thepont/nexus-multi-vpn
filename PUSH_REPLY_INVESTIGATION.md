# OpenVPN 3 PUSH_REPLY Investigation

## Summary

OpenVPN 3 connection is stuck **BEFORE** the PUSH_REPLY phase. The client never sends PUSH_REQUEST, and tun_builder methods are never called.

## Findings

### ✅ What's Working

1. **Config has required options:**
   - `pull` directive ✅ (required for PUSH_REPLY)
   - `client` directive ✅ (implies pull)
   - Config evaluation succeeds ✅

2. **Credentials provided:**
   - `provide_creds()` called successfully ✅
   - Credentials verified (24 bytes each) ✅
   - No auth failures reported ✅

3. **Connection flow:**
   - TLS handshake completes ✅
   - Certificates verified ✅
   - Config processed correctly ✅

### ❌ What's Not Working

1. **No PUSH_REQUEST sent:**
   - Client never requests configuration from server
   - No `PUSH_REQUEST` event logged

2. **No PUSH_REPLY received:**
   - Server never sends configuration
   - No `PUSH_REPLY` event logged

3. **tun_builder methods never called:**
   - `tun_builder_add_address()` - NOT called
   - `tun_builder_reroute_gw()` - NOT called
   - `tun_builder_establish()` - NOT called

4. **Connection stuck:**
   - OpenVPN Events: `RESOLVE → WAIT → CONNECTING → PAUSE`
   - Times out after 30 seconds
   - Never reaches PUSH_REQUEST phase

5. **Suspicious log:**
   - OpenVPN logs: `"Creds: UsernameEmpty/PasswordEmpty"`
   - This might indicate OpenVPN 3 isn't using provided credentials

## OpenVPN Connection Flow

```
1. Connect to server (network socket) ✅
2. TLS handshake (network socket) ✅
3. Authenticate (network socket) ✅
4. Send PUSH_REQUEST ← ❌ NOT REACHED
5. Receive PUSH_REPLY ← ❌ NOT REACHED
6. Call tun_builder methods ← ❌ NOT REACHED
7. Call tun_builder_establish() ← ❌ NOT REACHED
8. Connection complete ← ❌ NOT REACHED
```

## Code Analysis

### Config Processing (`openvpn_wrapper.cpp`)

- ✅ Removes unsupported options (`ping-timer-rem`, `remote-random`, `fast-io`, `comp-lzo`)
- ✅ Removes `auth-user-pass` (using `provide_creds()` instead)
- ✅ Adds `client-cert-not-required` if missing
- ✅ **Does NOT remove `pull` or `client`** ✅

### Credential Flow

1. `eval_config()` - Config evaluated ✅
2. `provide_creds()` - Credentials provided ✅
3. `connect()` - Connection started, but stuck at CONNECTING → PAUSE ❌

### Event Logging

Added logging for:
- `PUSH_REQUEST` - Client requesting config (never logged)
- `PUSH_REPLY` - Server sending config (never logged)
- `AUTH_PENDING` / `AUTH_OK` - Auth status (never logged)

## Possible Root Causes

### 1. Authentication Not Completing

**Symptom:** `"Creds: UsernameEmpty/PasswordEmpty"` log suggests credentials might not be used.

**Investigation:**
- Credentials are provided via `provide_creds()` after `eval_config()`
- Order is correct (eval_config → provide_creds → connect)
- But OpenVPN 3 might need credentials at a different time

**Possible fix:**
- Try calling `provide_creds()` before `eval_config()`
- Or provide credentials via config file instead of `provide_creds()`

### 2. OpenVPN 3 Waiting for TUN Before PUSH_REQUEST

**Symptom:** Connection stuck at CONNECTING → PAUSE, never sends PUSH_REQUEST.

**Investigation:**
- Standard OpenVPN uses network socket for PUSH_REQUEST (before TUN)
- But OpenVPN 3 ClientAPI might need TUN ready first
- Our `tun_builder_establish()` returns FD, but it's never called

**Possible fix:**
- Ensure TUN FD is set before `connect()`
- Or OpenVPN 3 might need TUN established before PUSH_REQUEST

### 3. Server Not Responding to Authentication

**Symptom:** Auth appears successful but server doesn't send PUSH_REPLY.

**Investigation:**
- No auth errors reported
- But server might be silently rejecting authentication
- Or requiring additional auth steps (2FA)

**Possible fix:**
- Check NordVPN server logs (if accessible)
- Verify credentials are correct and active
- Check if server requires 2FA

### 4. Network/Firewall Issue

**Symptom:** Connection established but no PUSH_REQUEST/PUSH_REPLY.

**Investigation:**
- TLS handshake completes (network works)
- But control channel might be blocked after auth

**Possible fix:**
- Check firewall rules
- Verify UDP port 1194 is open
- Test with different NordVPN server

### 5. OpenVPN 3 ClientAPI Bug/Incompatibility

**Symptom:** Works with OpenVPN 2.x but not OpenVPN 3.

**Investigation:**
- OpenVPN 3 ClientAPI might handle `pull` differently
- Or might have bugs with NordVPN's configuration

**Possible fix:**
- Check OpenVPN 3 GitHub issues
- Try different OpenVPN 3 version
- Or use OpenVPN 2.x client instead

## Recommendations

### Immediate Next Steps

1. **Check if credentials are actually used:**
   - Add logging to verify OpenVPN 3 receives credentials
   - Check if `"Creds: UsernameEmpty"` is just a log message or actual error

2. **Try providing credentials differently:**
   - Call `provide_creds()` before `eval_config()`
   - Or use auth file instead of `provide_creds()`

3. **Check OpenVPN 3 source code:**
   - Look for when PUSH_REQUEST is sent
   - Check if TUN FD is required before PUSH_REQUEST

4. **Test with OpenVPN 2.x client:**
   - Verify NordVPN config works with standard OpenVPN
   - Isolate if issue is OpenVPN 3-specific

5. **Increase verbosity:**
   - Add `verb 5` to config to see more OpenVPN logs
   - Check if there are hidden errors

### Long-term Solutions

1. **Switch to OpenVPN 2.x:**
   - More mature and better documented
   - Known to work with NordVPN
   - Better Android support

2. **Use different VPN protocol:**
   - WireGuard (modern, faster)
   - IKEv2/IPSec (native Android support)

3. **Contact OpenVPN 3 maintainers:**
   - Report issue if it's a bug
   - Get guidance on proper usage

## References

- [OpenVPN 3 ClientAPI Documentation](https://github.com/OpenVPN/openvpn3)
- [NordVPN OpenVPN Configuration](https://nordvpn.com/support/linux/)
- [OpenVPN PUSH_REPLY Troubleshooting](https://groups.google.com/g/tunnelblick-discuss)

## Logs Added

Added event logging in `openvpn_wrapper.cpp`:
- `PUSH_REQUEST` - Client requesting config
- `PUSH_REPLY` - Server sending config
- `AUTH_PENDING` / `AUTH_OK` - Auth status

These events will help identify where the connection is stuck.


