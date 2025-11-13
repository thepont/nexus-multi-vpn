# How to Regenerate NordVPN Service Credentials

## Step-by-Step Instructions

### 1. Log into NordVPN Dashboard
- Go to: **https://my.nordaccount.com/dashboard/nordvpn/**
- Log in with your NordVPN account credentials

### 2. Navigate to Service Credentials
- Look for a section titled **"Service Credentials"** or **"OpenVPN / IKEv2 credentials"**
- This is typically in the **"Advanced"** or **"Manual Setup"** section
- It's **separate** from your regular login credentials

### 3. Generate New Credentials
- Click **"Generate New Service Credentials"** or **"Regenerate"** button
- This will create a new username and password pair
- **Important**: Old Service Credentials will stop working once new ones are generated

### 4. Copy the Credentials
- You'll see:
  - **Service Username**: (usually 24 characters, alphanumeric)
  - **Service Password**: (usually 24 characters, alphanumeric)
- Copy each one **carefully** - no extra spaces or characters

### 5. Update Your .env File
- Open `/home/pont/projects/multi-region-vpn/.env`
- Replace the existing values:
  ```
  NORDVPN_USERNAME=<paste new username here>
  NORDVPN_PASSWORD=<paste new password here>
  ```
- **Important**: 
  - No quotes around the values
  - No spaces before or after the `=`
  - No trailing spaces after the credentials
  - Each value should be on its own line

### 6. Verify the Format
Run the verification script:
```bash
cd test-nordvpn-creds
./verify-credentials.sh
```

This should show:
- ✅ Username length: 24 chars
- ✅ Password length: 24 chars
- No warnings about whitespace

### 7. Test the New Credentials
```bash
cd test-nordvpn-creds
./test.sh
```

Expected result:
- ✅ CONNECTION SUCCESSFUL! (if credentials are valid)
- ❌ AUTHENTICATION FAILED (if still invalid)

## Alternative: If You Can't Find Service Credentials

Some NordVPN accounts may have Service Credentials in a different location:

1. **NordVPN Account Settings**:
   - Go to: https://my.nordaccount.com/
   - Look for "Advanced" or "Manual Setup" section

2. **NordVPN Website**:
   - Go to: https://nordvpn.com/
   - Log in → Account → Manual Setup

3. **Contact NordVPN Support**:
   - If you can't find Service Credentials, contact support
   - Ask specifically for "OpenVPN Service Credentials" or "Manual Setup Credentials"
   - Regular account login credentials **will NOT work** for OpenVPN

## Important Notes

- **Service Credentials ≠ Account Login**: 
  - Your regular email/password login is **NOT** the same as Service Credentials
  - Service Credentials are specifically for OpenVPN/IKEv2 manual connections

- **One Set Active at a Time**:
  - When you generate new Service Credentials, the old ones are deactivated
  - Make sure to update all places using the old credentials

- **Format**:
  - Service Credentials are typically 24 characters each
  - Alphanumeric (letters and numbers)
  - No special characters usually

## Troubleshooting

If regeneration doesn't work:
1. **Clear browser cache** and try again
2. **Try a different browser**
3. **Check account status** - ensure subscription is active
4. **Contact NordVPN support** - they can verify if Service Credentials are enabled for your account


