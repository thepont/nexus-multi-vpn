# NordVPN Credentials Tester

This Docker container tests NordVPN Service Credentials without affecting your host network.

## Usage

1. **Load credentials from `.env` file:**
```bash
source ../.env  # or set them manually
```

2. **Build the Docker image:**
```bash
docker build -t nordvpn-test .
```

3. **Run the test:**
```bash
docker run --rm \
  -e NORDVPN_USERNAME="$NORDVPN_USERNAME" \
  -e NORDVPN_PASSWORD="$NORDVPN_PASSWORD" \
  --network none \
  nordvpn-test
```

Or use the convenience script:
```bash
./test.sh
```

## What it does

1. Downloads the OpenVPN config for `uk1827.nordvpn.com` (same as our tests)
2. Prepares the config (removes unsupported options, adds `client-cert-not-required`)
3. Attempts to connect using the provided credentials
4. Analyzes the output and reports:
   - ✅ Success (credentials work)
   - ❌ Authentication failure (credentials invalid/wrong type)
   - ❌ Network/connection error
   - ❌ TLS/certificate error

## Network Isolation

The container uses `--network none` so it cannot affect your host network. It creates a dummy network interface (`--dev null`) that doesn't actually route traffic.

## Expected Output

If credentials are valid:
```
✅ CONNECTION SUCCESSFUL!
   Credentials are valid and connection works
```

If credentials are invalid:
```
❌ AUTHENTICATION FAILED
   This means the credentials are incorrect or not Service Credentials
```


