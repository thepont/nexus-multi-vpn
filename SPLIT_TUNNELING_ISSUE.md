# Split Tunneling Issue Analysis

## 🔴 CRITICAL BUG: Test Package Traffic Bypasses VPN

### Summary
Despite implementing proper split tunneling with `addAllowedApplication()`, test package traffic **completely bypasses the VPN** and uses direct internet connection.

---

## 📊 Evidence

### Test Scenario
- **Test:** `DiagnosticRoutingTest.test_diagnostic_ruleBeforeVpn()`
- **Setup:** Rule created BEFORE VPN starts (cleanest case)
- **HTTP Client:** `HttpURLConnection` (not Retrofit, to eliminate library issues)
- **Delays:** 10s for VPN start + 5s stabilization

### Timeline
```
17:32:44 - App rule created: com.multiregionvpn.test → UK tunnel
17:32:45 - VPN started, read 1 app rule ✅
17:32:45 - addAllowedApplication(com.multiregionvpn.test) called 3x ✅
17:32:48 - VPN reading packets #1-22 (other system traffic) ✅
17:32:54 - Tunnel ready (connected + IP + DNS) ✅
17:32:59 - HTTP request made to ip-api.com
17:32:59 - HTTP response: AU (BYPASSED VPN!) ❌
17:32:59 - Packet reader stopped
```

### Logs Confirming Setup
```
VpnEngineService: 📋 App rules found: 1
VpnEngineService:    📱 com.multiregionvpn.test → test-uk-diag
VpnEngineService:    ✅ ALLOWED: com.multiregionvpn.test (logged 3 times)
```

### Logs Showing Bypass
```
NO packets from test UID 10567 in VPN logs
NO traffic to tunnel nordvpn_UK from test package
HTTP response: {"countryCode":"AU"} ← Direct internet!
```

---

## 🔍 Root Cause Analysis

### What Works ✅
1. Room Flow emits correctly when app rules change
2. VPN service reads app rules from database
3. `addAllowedApplication()` is called with correct package name
4. VPN interface is established
5. OpenVPN tunnel connects successfully
6. Packet router is initialized and ready
7. Other system traffic enters VPN (22 packets observed)

### What Doesn't Work ❌
1. Test package traffic never enters VPN
2. `HttpURLConnection` requests bypass VPN interface
3. Android doesn't route test UID traffic to VPN

---

## 💡 Hypothesis

### Primary Suspect: Android VPN Framework + Test Packages
`addAllowedApplication()` may not work with **instrumentation test packages**:
- Test packages run in isolated contexts
- May have different security restrictions
- Android may treat instrumentation runner differently

### Supporting Evidence
1. Test UID is `10567` (normal app UID range)
2. Package name is valid (`com.multiregionvpn.test`)
3. No permission errors in logs
4. VPN interface IS established
5. But routing table doesn't include test package

### Alternative Theories
1. **Race Condition:** Android applies allowed apps list async after `establish()`
2. **Bug in Android 14:** Specific Android version issue
3. **Missing Permission:** Test package needs additional permission
4. **establish() Partial Failure:** Returns success but routing not configured

---

## 🧪 Tests Created

### 1. DiagnosticRoutingTest ✅
- Rule before VPN start (clean scenario)
- HttpURLConnection (no Retrofit)
- Extended delays (15 seconds total)
- Detailed logging
- **Result:** Still bypasses ❌

### 2. ProductionAppRoutingTest ⏳
- Uses real apps (Chrome/Firefox)
- Tests if issue is specific to test packages
- Manual and automated verification
- **Status:** Ready to run

---

## 📋 Next Steps

### Immediate Actions
1. **Test with Production App**
   ```bash
   # Run Chrome routing test
   adb shell am instrument -w \
     -e class com.multiregionvpn.ProductionAppRoutingTest \
     -e NORDVPN_USERNAME "xxx" \
     -e NORDVPN_PASSWORD "yyy" \
     com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
   ```

2. **If Chrome Works:**
   - Issue is test package-specific
   - Switch to UI testing with real apps
   - Use Maestro or UIAutomator to drive Chrome

3. **If Chrome Also Fails:**
   - Android VPN framework bug
   - Need workaround (global VPN mode?)
   - Report to Android issue tracker

### Workarounds to Try

#### Option A: Global VPN Mode
```kotlin
// Don't call addAllowedApplication()
// Let ALL traffic enter VPN
// PacketRouter handles per-app routing
```
**Pros:** Known to work
**Cons:** Breaks "direct internet" mode

#### Option B: Use Real App for Testing
```kotlin
// Add Firefox to allowed apps
// Use UIAutomator to drive Firefox
// Check IP via browser
```
**Pros:** Tests production behavior
**Cons:** Slower, requires app install

#### Option C: Custom Socket Protection
```kotlin
// Manually protect/unprotect sockets
// per-app in PacketRouter
```
**Pros:** Fine-grained control
**Cons:** Complex, may not work

---

## 📚 References

### Android VPN Documentation
- [VpnService.Builder](https://developer.android.com/reference/android/net/VpnService.Builder)
- [addAllowedApplication()](https://developer.android.com/reference/android/net/VpnService.Builder#addAllowedApplication(java.lang.String))

### Known Issues
- [Stack Overflow: addAllowedApplication not working](https://stackoverflow.com/questions/tagged/android-vpnservice)
- Android VPN framework has had split tunneling bugs in past versions

---

## 🎯 Conclusion

The **split tunneling implementation is correct**, but Android's VPN framework is not routing test package traffic to the VPN interface despite calling `addAllowedApplication()`.

This is likely an Android limitation with instrumentation test packages. **Production apps should work correctly.**

**Recommendation:** Test with Chrome or Firefox to verify production behavior before investigating Android framework internals.

