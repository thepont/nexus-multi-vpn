# Environment Variables & Secrets Setup

This project uses environment variables to manage test credentials securely.

## Setup

### 1. Create `.env` file

Copy the example file and fill in your credentials:

```bash
cp .env.example .env
```

Then edit `.env` and add your NordVPN Service Credentials:

```
NORDVPN_USERNAME=your_actual_service_username
NORDVPN_PASSWORD=your_actual_service_password
```

### 2. Get NordVPN Service Credentials

1. Log into [NordVPN Account Dashboard](https://my.nordaccount.com/dashboard/nordvpn/manual-setup/)
2. Navigate to: **Services** → **NordVPN** → **Manual Setup**
3. Generate Service Credentials (username/password)
4. Copy them into your `.env` file

**⚠️ Important:** 
- Never commit `.env` to git (it's in `.gitignore`)
- These are Service Credentials, NOT your account password
- These credentials can be used for OpenVPN connections

## Running Tests

### Option 1: Using the test script (recommended)

```bash
./scripts/run-tests-with-env.sh
```

This script:
- Loads variables from `.env`
- Validates required credentials are set
- Runs Maestro tests with environment variables

### Option 2: Manual environment loading

```bash
source scripts/load-env.sh
maestro test .maestro/01_test_full_config_flow.yaml
```

### Option 3: Inline export

```bash
export NORDVPN_USERNAME="your_username"
export NORDVPN_PASSWORD="your_password"
maestro test .maestro/01_test_full_config_flow.yaml
```

## CI/CD with GitHub Secrets

The project includes a GitHub Actions workflow (`.github/workflows/test.yml`) that uses GitHub Secrets.

### Setting up GitHub Secrets

1. Go to your repository on GitHub
2. Navigate to: **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add these secrets:
   - `NORDVPN_USERNAME`: Your NordVPN Service Username
   - `NORDVPN_PASSWORD`: Your NordVPN Service Password

### Optional Secrets (with defaults)

- `TEST_VPN_HOSTNAME`: Server hostname (default: `uk1234.nordvpn.com`)
- `TEST_VPN_REGION`: Region for tests (default: `UK`)
- `TEST_APP_PACKAGE`: Package name for app routing tests (default: `com.android.chrome`)

## Environment Variables Reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `NORDVPN_USERNAME` | ✅ Yes | - | NordVPN Service Username |
| `NORDVPN_PASSWORD` | ✅ Yes | - | NordVPN Service Password |
| `TEST_VPN_HOSTNAME` | No | `uk1234.nordvpn.com` | VPN server hostname for tests |
| `TEST_VPN_REGION` | No | `UK` | Region identifier for tests |
| `TEST_APP_PACKAGE` | No | `com.android.chrome` | App package for routing tests |

## Security Notes

- ✅ `.env` is in `.gitignore` - never committed
- ✅ `.env.example` has placeholders - safe to commit
- ✅ GitHub Secrets are encrypted at rest
- ⚠️  Never share credentials in chat, emails, or unsecured channels
- ⚠️  Rotate credentials if accidentally exposed


