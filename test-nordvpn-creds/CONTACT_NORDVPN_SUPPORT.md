# NordVPN Service Credentials - No Regenerate Button

## Important Discovery

**NordVPN Service Credentials are static** - they cannot be regenerated through the dashboard. You must contact NordVPN support to change them.

## Why Your Credentials Might Be Failing

Your credentials are in the correct format (24 chars each), but authentication is failing. This could mean:

1. **Credentials are incorrect** - might have been copied wrong originally
2. **Credentials are expired/disabled** - NordVPN may have disabled them
3. **Account issue** - Service Credentials might not be properly enabled for your account

## How to Contact NordVPN Support

### Option 1: Live Chat (Recommended)
1. Go to: https://support.nordvpn.com/
2. Click "Contact Us" or "Live Chat"
3. Tell them: **"I need to reset my OpenVPN Service Credentials for manual setup"**
4. Explain: "My Service Credentials are not working for OpenVPN connections"

### Option 2: Email Support
- Email: support@nordvpn.com
- Subject: "Request to Reset OpenVPN Service Credentials"
- Include:
  - Your account email
  - Reason: "Service Credentials authentication failing for OpenVPN manual setup"

### Option 3: Support Ticket
1. Go to: https://support.nordvpn.com/
2. Submit a support ticket
3. Category: "Account & Billing" or "Technical Issues"
4. Request: "Reset OpenVPN Service Credentials"

## What to Tell NordVPN Support

**Template message:**
```
Hi,

I'm trying to use OpenVPN Service Credentials for manual VPN setup 
(third-party OpenVPN client). The credentials I have are not working - 
I'm getting AUTH_FAILED errors.

Could you please:
1. Verify my account has Service Credentials enabled
2. Reset/regenerate my Service Credentials for OpenVPN
3. Provide the new credentials

My account email: [your email]

Thank you!
```

## After Getting New Credentials

1. Update `.env` file:
   ```
   NORDVPN_USERNAME=<new username>
   NORDVPN_PASSWORD=<new password>
   ```

2. Test immediately:
   ```bash
   cd test-nordvpn-creds
   ./test.sh
   ```

## Alternative: Check Current Credentials Location

If you can see your Service Credentials in the dashboard:
- They might be displayed but not working
- Still contact support to verify they're active
- They may need to be reset even if they're visible

## Timeline

- Live chat: Usually immediate (during business hours)
- Email: 24-48 hours response time
- Support ticket: 24-48 hours response time

## Important Notes

- **Service Credentials ≠ Account Password**: These are different
- **One set at a time**: When support resets them, old ones stop working
- **Keep them secure**: Don't share Service Credentials publicly


