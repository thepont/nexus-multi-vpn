# Running Local E2E Tests

## Prerequisites

✅ Docker is running on your host machine

## Quick Start

### 1. Start Docker Compose Environment

For **routing test**:
```bash
cd app/src/androidTest/resources/docker-compose
docker-compose -f docker-compose.routing.yaml up -d
```

For **DNS test**:
```bash
docker-compose -f docker-compose.dns.yaml up -d
```

For **conflict test**:
```bash
docker-compose -f docker-compose.conflict.yaml up -d
```

### 2. Verify Services Are Running

```bash
docker-compose -f docker-compose.routing.yaml ps
```

You should see containers running:
- `vpn-server-uk`
- `vpn-server-fr`
- `http-server-uk`
- `http-server-fr`

### 3. Run Tests

**Option 1: Run all tests**
```bash
./gradlew :app:connectedAndroidTest
```

**Option 2: Run specific test class**
```bash
adb shell am instrument -w \
  -e class com.multiregionvpn.LocalRoutingTest \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
```

**Option 3: Run specific test method**
```bash
adb shell am instrument -w \
  -e class com.multiregionvpn.LocalRoutingTest#test_simultaneousPerAppRouting \
  com.multiregionvpn.test/androidx.test.runner.AndroidJUnitRunner
```

## Host Machine IP

- **Android Emulator**: Uses `10.0.2.2` (default gateway to host)
- **Physical Device**: You may need to configure your host machine's IP address

To find your host machine IP:
```bash
# On Linux/Mac
ip addr show | grep "inet " | grep -v 127.0.0.1

# Or use hostname
hostname -I
```

## Service Ports

The following ports are exposed on your host machine:

| Service | Port | Description |
|---------|------|-------------|
| VPN UK | 1194 | UK VPN server |
| VPN FR | 1195 | FR VPN server |
| HTTP UK | 8080 | UK HTTP server |
| HTTP FR | 8081 | FR HTTP server |
| VPN DNS | 1196 | DNS VPN server |
| HTTP DNS | 8082 | DNS HTTP server |
| VPN UK (Conflict) | 1197 | UK VPN (conflict test) |
| VPN FR (Conflict) | 1198 | FR VPN (conflict test) |
| HTTP UK (Conflict) | 8083 | UK HTTP (conflict test) |
| HTTP FR (Conflict) | 8084 | FR HTTP (conflict test) |

## Troubleshooting

### Docker Compose Not Starting

```bash
# Check Docker is running
docker ps

# Check Docker Compose version
docker-compose --version

# View logs
docker-compose -f docker-compose.routing.yaml logs
```

### Services Not Accessible from Android

1. **Check firewall**: Ensure ports are not blocked
2. **Check host IP**: Verify `10.0.2.2` works for emulator
3. **Check port forwarding**: For physical devices, ensure ports are accessible

### Test Failures

1. **Check Docker logs**: `docker-compose logs`
2. **Check Android logs**: `adb logcat | grep -i vpn`
3. **Verify VPN permission**: Tests auto-grant, but check if needed manually:
   ```bash
   adb shell appops set com.multiregionvpn ACTIVATE_VPN allow
   ```

## Stopping Docker Compose

After tests complete:
```bash
docker-compose -f docker-compose.routing.yaml down
docker-compose -f docker-compose.dns.yaml down
docker-compose -f docker-compose.conflict.yaml down
```

Or stop all:
```bash
docker-compose -f docker-compose.*.yaml down
```


