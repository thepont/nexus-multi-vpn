# WireGuard Test Environment

This Docker setup provides two WireGuard VPN servers for testing multi-tunnel routing.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Docker Network (172.20.0.0/16)          │
│                                                              │
│  ┌──────────────────┐              ┌──────────────────┐    │
│  │  WireGuard UK    │              │  WireGuard FR    │    │
│  │  172.20.0.10     │              │  172.20.0.20     │    │
│  │  :51820          │              │  :51821          │    │
│  │  VPN: 10.13.13.1 │              │  VPN: 10.14.14.1 │    │
│  └────────┬─────────┘              └────────┬─────────┘    │
│           │                                 │               │
│  ┌────────▼─────────┐              ┌───────▼──────────┐    │
│  │  Web UK          │              │  Web FR          │    │
│  │  172.20.0.11     │              │  172.20.0.21     │    │
│  │  (Mock ip-api)   │              │  (Mock ip-api)   │    │
│  └──────────────────┘              └──────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ Android VPN Client
                           ▼
                    ┌──────────────┐
                    │ Pixel 6 /    │
                    │ Emulator     │
                    └──────────────┘
```

## Quick Start

```bash
cd docker-wireguard-test
chmod +x setup.sh
./setup.sh
```

This will:
1. Create WireGuard UK and France servers
2. Generate client configs
3. Create mock web servers (simulate ip-api.com)
4. Display connection details

## Client Configurations

After running `setup.sh`, client configs will be available at:

- **UK**: `wireguard-uk/peer_android_client/peer_android_client.conf`
- **France**: `wireguard-fr/peer_android_client/peer_android_client.conf`

### Example Config Format

```ini
[Interface]
PrivateKey = <base64-private-key>
Address = 10.13.13.2/32
DNS = 10.13.13.1

[Peer]
PublicKey = <base64-public-key>
Endpoint = 127.0.0.1:51820
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
```

## Testing

### 1. Verify Servers Are Running

```bash
docker ps
# Should show: wg-test-uk, wg-test-fr, web-uk, web-fr
```

### 2. Test Web Endpoints

```bash
# UK endpoint
curl http://172.20.0.11
# Should return: {"country": "GB", ...}

# France endpoint
curl http://172.20.0.21
# Should return: {"country": "France", ...}
```

### 3. Test WireGuard Connection (from host)

```bash
# Install wg-quick if not already installed
# sudo apt install wireguard-tools

# Copy UK config
sudo cp wireguard-uk/peer_android_client/peer_android_client.conf /etc/wireguard/wg-uk.conf

# Bring up UK tunnel
sudo wg-quick up wg-uk

# Test connection
curl --interface wg-uk http://172.20.0.11

# Bring down tunnel
sudo wg-quick down wg-uk
```

### 4. Run E2E Tests

```bash
cd ..
./gradlew :app:connectedDebugAndroidTest
```

## Configuration Details

### Network
- **Subnet**: 172.20.0.0/16
- **UK VPN Subnet**: 10.13.13.0/24
- **France VPN Subnet**: 10.14.14.0/24

### Ports
- **UK WireGuard**: 51820/udp
- **France WireGuard**: 51821/udp

### DNS
- **UK**: 10.13.13.1 (provided by WireGuard server)
- **France**: 10.14.14.1 (provided by WireGuard server)

## Troubleshooting

### Containers not starting
```bash
# Check logs
docker-compose logs wireguard-uk
docker-compose logs wireguard-fr

# Ensure kernel modules are loaded
sudo modprobe wireguard
```

### Can't connect from Android
1. Check that ports are exposed: `docker ps`
2. Verify firewall rules: `sudo iptables -L`
3. Ensure Android device/emulator can reach host
4. Check WireGuard logs: `docker logs wg-test-uk`

### Config files not generated
```bash
# Restart containers to regenerate configs
docker-compose down
rm -rf wireguard-uk wireguard-fr
./setup.sh
```

## Cleanup

```bash
# Stop containers
docker-compose down

# Remove volumes
docker-compose down -v

# Remove all files
cd ..
rm -rf docker-wireguard-test
```

## Integration with E2E Tests

The E2E tests will:
1. Load WireGuard configs from `app/src/androidTest/assets/`
2. Create VPN tunnels using `WireGuardVpnClient`
3. Route test app traffic through appropriate tunnel
4. Verify traffic emerges from correct "country"

See `app/src/androidTest/java/com/multiregionvpn/WireGuardE2ETest.kt` for test implementation.

