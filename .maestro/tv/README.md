# Google TV / Android TV Tests

Maestro UI tests specifically designed for **Google TV** and **Android TV** using D-pad navigation.

## 🎮 TV vs Phone Testing

### Key Differences
| Feature | Phone | Google TV |
|---------|-------|-----------|
| Input | Touch | D-pad / Remote |
| Navigation | Tap | Arrow keys + Enter |
| UI | Touch-optimized | 10-foot interface |
| Text Input | Keyboard | On-screen keyboard |
| Focus | Implicit | Explicit focus system |

---

## 📺 Prerequisites

### 1. Google TV Device or Emulator
```bash
# Option A: Physical Google TV device (Chromecast, Sony TV, etc.)
adb connect TV_IP:5555

# Option B: Android TV emulator
emulator -avd Android_TV_1080p_API_34
```

### 2. App Installed on TV
```bash
# Build and install
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Grant VPN permission
adb shell appops set com.multiregionvpn ACTIVATE_VPN allow
```

### 3. Environment Variables
```bash
export NORDVPN_USERNAME="your_username"
export NORDVPN_PASSWORD="your_password"
```

---

## 🧪 TV Test Files

### 01_tv_navigation_dpad.yaml
Tests D-pad navigation between tabs and UI elements.

**What it tests:**
- ✅ D-pad RIGHT to navigate tabs
- ✅ D-pad ENTER to select
- ✅ D-pad UP to access header
- ✅ VPN toggle with remote control
- ✅ All tabs accessible

**Run:**
```bash
maestro test .maestro/tv/01_tv_navigation_dpad.yaml
```

**Duration:** ~60 seconds

---

### 02_tv_complete_workflow.yaml
Complete user journey on TV using only remote control.

**What it tests:**
- ✅ Navigate to Settings via D-pad
- ✅ Enter credentials with on-screen keyboard
- ✅ Save credentials
- ✅ Navigate to Tunnels tab
- ✅ Open Add Tunnel dialog
- ✅ Toggle VPN on/off

**Run:**
```bash
NORDVPN_USERNAME="xxx" NORDVPN_PASSWORD="yyy" \
maestro test .maestro/tv/02_tv_complete_workflow.yaml
```

**Duration:** ~90 seconds

---

## 🎯 Running on Different TV Devices

### Chromecast with Google TV
```bash
# Find device IP in Settings → System → About
adb connect CHROMECAST_IP:5555
maestro test .maestro/tv/
```

### Sony / TCL / Hisense Google TV
```bash
# Enable Developer Mode, then Wireless Debugging
adb connect TV_IP:5555
maestro test .maestro/tv/
```

### NVIDIA Shield TV
```bash
adb connect SHIELD_IP:5555
maestro test .maestro/tv/
```

### Android TV Emulator
```bash
# Start emulator
emulator -avd Android_TV_1080p_API_34 &

# Run tests (auto-detects emulator)
maestro test .maestro/tv/
```

---

## 🎮 D-pad Key Mappings

### Maestro Remote Control Keys
```yaml
- pressKey: Up        # D-pad UP
- pressKey: Down      # D-pad DOWN
- pressKey: Left      # D-pad LEFT
- pressKey: Right     # D-pad RIGHT
- pressKey: Enter     # D-pad CENTER / OK
- pressKey: Back      # Back button
- pressKey: Home      # Home button
```

### Navigation Pattern
```
Header (Toggle Switch)
        ↑
        ↓
Content (Tabs, Lists)
        ↑
        ↓
Bottom Navigation
  ← Tunnels  Apps  Connections  Settings →
```

---

## ⚠️ TV-Specific Considerations

### 1. Focus System
On TV, only ONE element is focused at a time. Tests must:
- Navigate to element using arrow keys
- Press ENTER to select
- Handle focus changes explicitly

### 2. Text Input
On-screen keyboard appears for text fields:
- D-pad to select letters (slow)
- OR use `inputText` (Maestro simulates keyboard)

### 3. Dialogs
Dialogs require D-pad navigation:
- DOWN to navigate options
- ENTER to select
- BACK to cancel

### 4. Scrolling
Use D-pad UP/DOWN for scrolling:
```yaml
- pressKey: Down  # Scroll down
  repeat: 5
```

---

## 🐛 Troubleshooting

### Test fails with "Element not found"
- Elements may be off-screen on TV
- Use D-pad to scroll first
- Check if element is focusable

### Text input doesn't work
- On-screen keyboard may be different on TV
- Try using `inputText` with delay
- Verify keyboard is open before typing

### Navigation gets lost
- TV focus system is different from phone
- Add more `wait` delays between D-pad presses
- Check focus indicators in UI

### App doesn't launch on TV
- Verify app has TV launcher activity
- Check AndroidManifest has leanback launcher
- Add TV banner if needed

---

## 📋 Google TV App Checklist

### Required for TV
- ✅ Leanback launcher (for TV home screen)
- ✅ D-pad navigation support
- ✅ Focus indicators
- ✅ Large touch targets (48dp min)
- ✅ Readable fonts (16sp min)
- ✅ High contrast colors

### Optional for TV
- ⚠️ TV banner (320x180 image)
- ⚠️ TV-specific layouts
- ⚠️ Remote control shortcuts
- ⚠️ Voice search integration

---

## 📊 Test Coverage

### UI Components (TV)
- ✅ Header bar (readable from 10 feet)
- ✅ Bottom navigation (D-pad accessible)
- ✅ Tunnel list (focusable items)
- ✅ App list (scrollable with D-pad)
- ✅ VPN toggle (remote control accessible)
- ✅ Dialogs (D-pad navigation)

### User Flows (TV)
- ✅ Launch app
- ✅ Navigate tabs with remote
- ✅ Toggle VPN with D-pad
- ✅ Enter text with TV keyboard
- ✅ Scroll lists with D-pad

---

## 🚀 Quick Start

```bash
# 1. Start Android TV emulator
emulator -avd Android_TV_1080p_API_34 &

# 2. Install app
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Run TV tests
maestro test .maestro/tv/

# 4. Watch the remote control simulation!
```

---

## 📱 Running on Your Pixel

Even though your Pixel isn't a TV, you can simulate D-pad navigation:

```bash
# Connect to Pixel
adb connect 192.168.68.73:PORT

# Run TV tests (will use D-pad simulation)
maestro test .maestro/tv/01_tv_navigation_dpad.yaml
```

This verifies the app works with **keyboard navigation**, useful for:
- Google TV
- Android TV
- Bluetooth keyboards
- Accessibility navigation

---

## ✅ Benefits

### For Users
- ✅ Works on Google TV (Chromecast, Smart TVs)
- ✅ Route streaming apps through VPN on TV
- ✅ Control with TV remote
- ✅ Readable from couch (10-foot UI)

### For Testing
- ✅ Automated D-pad navigation tests
- ✅ Verify TV compatibility
- ✅ Test keyboard accessibility
- ✅ Validate focus system

---

## 📖 Next Steps

### To Enable Full TV Support
1. Add leanback launcher to AndroidManifest
2. Create TV banner image (320x180)
3. Add TV-specific layouts (optional)
4. Test on real Google TV device

### To Run Tests Now
```bash
# Wait for Pixel to reconnect, then:
maestro test .maestro/tv/01_tv_navigation_dpad.yaml
```

This simulates TV remote control on your phone!

