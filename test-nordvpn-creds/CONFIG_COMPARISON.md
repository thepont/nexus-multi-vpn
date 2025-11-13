# Config Comparison Guide

## The Question: Are We Using Outdated Configs?

### Current Method
- **Source**: `https://downloads.nordcdn.com/configs/files/ovpn_udp/servers/{hostname}.udp.ovpn`
- **Access**: Public (no authentication required)
- **Last Modified**: Checked today - configs are current
- **Format**: Standard OpenVPN config with `auth-user-pass`

### Alternative Method (Dashboard)
- **Source**: Dashboard → "Set up NordVPN manually" → Download config
- **Access**: Requires login + email verification
- **Possibility**: Might include special tokens or different authentication

## How to Test

### Step 1: Download Config from Dashboard
1. Go to: https://my.nordaccount.com/dashboard/nordvpn/
2. Click "Set up NordVPN manually"
3. Complete email verification if needed
4. Download a config file (e.g., `uk1827.nordvpn.com.udp.ovpn`)
5. Save it as `/tmp/dashboard-config.ovpn`

### Step 2: Compare Configs
```bash
# Download public config
curl -s "https://downloads.nordcdn.com/configs/files/ovpn_udp/servers/uk1827.nordvpn.com.udp.ovpn" > /tmp/public-config.ovpn

# Compare
diff /tmp/public-config.ovpn /tmp/dashboard-config.ovpn
```

### Step 3: Test with Dashboard Config
```bash
cd test-nordvpn-creds
# Replace the config URL in test-credentials.sh with local file
# Or use the dashboard config directly
./test.sh
```

## What to Look For

### Differences to Check:
1. **Authentication directives**: Is `auth-user-pass` the same?
2. **Special tokens**: Are there any API tokens or session IDs?
3. **Server endpoints**: Are the `remote` addresses different?
4. **Certificate differences**: Are the CA certificates different?
5. **Additional directives**: Any new config options?

### If They're Different:
- Update `VpnTemplateService.kt` to use dashboard configs
- Or use API with credentials to get authenticated configs
- Or download configs from dashboard and bundle them

## Current Status

- ✅ Public configs are current (last modified today)
- ✅ Config format is standard OpenVPN
- ❌ Authentication still fails with Service Credentials
- ❓ Need to test if dashboard configs work differently


