# NordVPN Service Credentials Activation Guide

## Critical Discovery

Based on testing and research, **NordVPN Service Credentials require email verification** to be activated.

## The Activation Process

### Step 1: Access Manual Setup
1. Go to: **https://my.nordaccount.com/dashboard/nordvpn/**
2. Look for: **"Set up NordVPN manually"** or **"Manual Setup"**
3. Click on it

### Step 2: Email Verification
- You should receive a **verification code via email**
- Enter this code in the dashboard
- **This activates the Service Credentials**

### Step 3: Get Activated Credentials
- After verification, the Service Credentials will be displayed
- These are NOW active and ready to use
- Copy them to your `.env` file

## Important Notes

⚠️ **Credentials without email verification are NOT active**:
- Even if you see credentials in the dashboard
- Even if they look correct (24 chars each)
- They won't work until you complete the email verification

## Why Your Credentials Are Failing

Your credentials are:
- ✅ Correctly formatted (24 chars each)
- ✅ Properly encoded (UTF-8)
- ✅ Being sent correctly to NordVPN
- ❌ **Not activated** (missing email verification)

## What to Do Now

1. **Go to the dashboard**: https://my.nordaccount.com/dashboard/nordvpn/
2. **Click "Set up NordVPN manually"**
3. **Check your email** for the verification code
4. **Enter the code** in the dashboard
5. **Copy the NEW credentials** (they might be the same or different)
6. **Update `.env`** with the new credentials
7. **Test again**: `cd test-nordvpn-creds && ./test.sh`

## If You Don't See "Set up NordVPN manually"

Your account might:
- Not have this feature enabled
- Need to contact support to enable it
- Have a different account type

**Contact NordVPN support** and ask:
- "I need to set up OpenVPN manually but don't see the option"
- "Can you enable Service Credentials for my account?"
- "I need the email verification process for manual setup"

## Testing After Activation

Once you have activated credentials:
```bash
cd test-nordvpn-creds
./test.sh
```

Expected result: **✅ CONNECTION SUCCESSFUL!**


