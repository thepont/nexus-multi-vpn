# Test Suite Quick Start Guide

## 🚀 Get Started in 5 Minutes

### Step 1: Setup Environment

```bash
# Run the setup script
bash app/src/androidTest/resources/setup-test-environment.sh
```

This will:
- ✅ Check prerequisites (Docker, docker-compose)
- ✅ Create necessary directories
- ✅ Generate OpenVPN server configurations
- ✅ Create HTTP server content

### Step 2: Generate PKI Certificates

```bash
# Generate PKI certificates for all test servers
bash app/src/androidTest/resources/openvpn-configs/generate-pki.sh
```

**Note:** This requires Docker to be running and may take a few minutes.

### Step 3: Build and Install Test Apps (Optional)

For full end-to-end validation, create test apps:

```bash
# See test-apps/README.md for app requirements
# Then install:
adb install app/src/androidTest/resources/test-apps/test-app-uk.apk
adb install app/src/androidTest/resources/test-apps/test-app-fr.apk
adb install app/src/androidTest/resources/test-apps/test-app-dns.apk
```

### Step 4: Run Tests

```bash
# Run all local tests
./gradlew :app:connectedAndroidTest

# Or run individual test suites
adb shell am instrument -w \
  -e class com.multiregionvpn.LocalRoutingTest \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
```

## 📋 Test Suites

### Suite 1: NordVpnE2ETest (Production)
```bash
source .env
adb shell am instrument -w \
  -e NORDVPN_USERNAME "$NORDVPN_USERNAME" \
  -e NORDVPN_PASSWORD "$NORDVPN_PASSWORD" \
  -e class com.multiregionvpn.NordVpnE2ETest \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
```

### Suite 2: LocalRoutingTest (Multi-Tunnel)
```bash
adb shell am instrument -w \
  -e class com.multiregionvpn.LocalRoutingTest \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
```

### Suite 3: LocalDnsTest (DNS/DHCP)
```bash
adb shell am instrument -w \
  -e class com.multiregionvpn.LocalDnsTest \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
```

### Suite 4: LocalConflictTest (Subnet Conflict)
```bash
adb shell am instrument -w \
  -e class com.multiregionvpn.LocalConflictTest \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
```

## 🔍 Troubleshooting

### Docker Compose Not Starting
- Check Docker is running: `docker ps`
- Check docker-compose version: `docker-compose --version`
- Verify Docker Compose files are in correct location

### OpenVPN Connection Fails
- Verify PKI certificates are generated
- Check server configs are correct
- Ensure ports are not already in use

### Test Apps Not Found
- Tests will still validate infrastructure
- Install test apps for full validation
- See `test-apps/README.md` for app requirements

### VPN Permission Issues
- Tests automatically grant VPN permission via appops
- If issues persist, manually run:
  ```bash
  adb shell appops set com.multiregionvpn ACTIVATE_VPN allow
  ```

## 📚 Documentation

- **Full Overview**: `TEST_SUITE_OVERVIEW.md`
- **Implementation Status**: `TEST_SUITE_COMPLETE.md`
- **Docker Compose Setup**: `docker-compose/README.md`
- **Test App Requirements**: `test-apps/README.md`

## ✅ Verification

After setup, verify everything works:

```bash
# Check Docker Compose is ready
docker-compose -f app/src/androidTest/resources/docker-compose/docker-compose.routing.yaml ps

# Check test apps are installed (if created)
adb shell pm list packages | grep testapp

# Run a quick test
./gradlew :app:assembleDebugAndroidTest
```

## 🎯 Next Steps

1. **Complete Setup**: Generate PKI certificates
2. **Create Test Apps** (optional): For full validation
3. **Run Tests**: Verify all test suites pass
4. **CI/CD Integration**: Add to your CI pipeline

## 💡 Tips

- Tests automatically manage Docker Compose lifecycle
- Tests gracefully handle missing test apps
- All tests provide detailed output and setup instructions
- Infrastructure validation works without test apps


