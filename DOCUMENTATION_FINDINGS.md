# OpenVPN 3 Documentation Findings

## Summary

Based on examination of OpenVPN 3 ClientAPI documentation, here are the possible causes for the connection hanging before PUSH_REQUEST:

## 1. Credential Timing Issues

### Finding:
OpenVPN 3 ClientAPI may require credentials to be provided at a specific stage or via a callback mechanism.

### Evidence from Documentation:
- `provide_creds()` must be called at the correct stage in the connection process
- Credentials may need to be provided via a callback (e.g., `need_creds()`) instead of just `provide_creds()`
- The `"Creds: UsernameEmpty"` log message might indicate credentials are being checked before they're provided or in the wrong format

### Current Implementation:
```cpp
// Our sequence:
1. eval_config() ✅
2. provide_creds() ✅
3. connect() ✅
```

### Possible Issue:
- OpenVPN 3 might need credentials provided via callback during `connect()`, not before
- Or credentials might need to be in Config object, not just via `provide_creds()`

## 2. TunBuilderBase Implementation

### Finding:
Missing `tun_builder_persist()` implementation could cause issues.

### Evidence from Documentation:
- If `tun_builder_persist()` returns `true`, OpenVPN 3 may try to reuse an existing TUN interface
- This could cause issues if the TUN interface is not in the expected state
- We set `tunPersist = false` in Config, but we don't implement the method

### Current Implementation:
```cpp
// We implement:
✅ tun_builder_add_address()
✅ tun_builder_reroute_gw()
✅ tun_builder_add_route()
✅ tun_builder_set_dns_options()
✅ tun_builder_set_mtu()
✅ tun_builder_set_session_name()
✅ tun_builder_establish()

// Missing:
❌ tun_builder_persist() - Not implemented
```

### Possible Issue:
- If `tun_builder_persist()` is not implemented, OpenVPN 3 might use a default behavior
- This could cause it to try to reuse TUN interfaces incorrectly

## 3. Configuration Options

### Finding:
The `pull` option is required for PUSH_REQUEST.

### Evidence from Documentation:
- Client configuration MUST include `pull` or `client` option
- Without `pull`, client won't send PUSH_REQUEST
- This causes connection to hang

### Current Implementation:
✅ Config has `pull` and `client` options (not removed)
✅ Config evaluation succeeds

### Status:
✅ This is NOT the issue - we have `pull` in config

## 4. Connection Sequence

### Standard OpenVPN Flow:
1. Connect to server (network socket)
2. TLS handshake (network socket)
3. Authenticate (network socket)
4. Send PUSH_REQUEST (network socket) ← **STUCK HERE**
5. Receive PUSH_REPLY (network socket)
6. Call tun_builder methods
7. Call tun_builder_establish()

### Current Status:
- Steps 1-3 complete ✅
- Step 4 never happens ❌
- Steps 5-7 never happen ❌

## 5. Possible Root Causes

### A. Credential Callback Not Implemented
**Hypothesis:** OpenVPN 3 expects credentials via callback during `connect()`, not via `provide_creds()` before `connect()`.

**Evidence:**
- Documentation mentions credentials may need to be provided via callback
- `"Creds: UsernameEmpty"` log appears during `connect()`, not before
- We provide credentials before `connect()`, but OpenVPN 3 might check them during `connect()`

**Solution:**
- Check if `need_creds()` callback needs to be implemented
- Or provide credentials during `connect()` callback, not before

### B. TunBuilderBase Methods Not Complete
**Hypothesis:** Missing `tun_builder_persist()` or other methods causes OpenVPN 3 to hang.

**Evidence:**
- Documentation mentions `tun_builder_persist()` affects TUN reuse behavior
- We don't implement this method
- OpenVPN 3 might be waiting for this method to be called

**Solution:**
- Implement `tun_builder_persist()` to return `false` (since we set `tunPersist = false`)

### C. Authentication Completes But Server Doesn't Respond
**Hypothesis:** Authentication succeeds, but server doesn't send PUSH_REPLY.

**Evidence:**
- No AUTH_FAILED event (so authentication succeeds)
- But no PUSH_REQUEST sent (so client doesn't request config)
- Connection hangs at CONNECTING → PAUSE

**Possible Causes:**
- Server requires additional authentication step (2FA)
- Server requires client certificate (not just username/password)
- Server configuration issue
- Network issue preventing server response

### D. OpenVPN 3 ClientAPI Bug
**Hypothesis:** There's a bug in OpenVPN 3 ClientAPI that prevents PUSH_REQUEST.

**Evidence:**
- All requirements met (pull option, credentials, config)
- But PUSH_REQUEST never sent
- Connection hangs without error

**Solution:**
- Check OpenVPN 3 GitHub issues
- Try different OpenVPN 3 version
- Or use OpenVPN 2.x instead

## Recommendations

### Immediate Actions:

1. **Implement `tun_builder_persist()`:**
   ```cpp
   virtual bool tun_builder_persist() override {
       return false;  // Match our tunPersist = false config
   }
   ```

2. **Check for credential callback:**
   - Look for `need_creds()` or similar callback in OpenVPNClient
   - Implement if required
   - Or try providing credentials during `connect()` callback

3. **Increase verbosity:**
   - Add `verb 5` to config
   - Check for more detailed logs about why PUSH_REQUEST isn't sent

4. **Check OpenVPN 3 source code:**
   - Find where "Creds: UsernameEmpty" is logged
   - Understand when/why it's logged
   - Check if credentials are actually used from `provide_creds()`

### Long-term Solutions:

1. **Try OpenVPN 2.x:**
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

- OpenVPN 3 ClientAPI Documentation: https://openvpn.github.io/openvpn3/
- TunBuilderBase Documentation: https://openvpn.github.io/openvpn3/classopenvpn_1_1TunBuilderBase.html
- OpenVPN 3 GitHub: https://github.com/OpenVPN/openvpn3-client


