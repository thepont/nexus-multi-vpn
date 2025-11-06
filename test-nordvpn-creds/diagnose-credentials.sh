#!/bin/bash
# Comprehensive credential diagnostic

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "═══════════════════════════════════════════════════════"
echo "🔍 COMPREHENSIVE NORDVPN CREDENTIAL DIAGNOSTIC"
echo "═══════════════════════════════════════════════════════"
echo ""

# Load credentials
if [ -f "$PROJECT_ROOT/.env" ]; then
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
else
    echo "❌ .env file not found"
    exit 1
fi

echo "1️⃣ CREDENTIAL FORMAT ANALYSIS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Username: '${NORDVPN_USERNAME}'"
echo "  Length: ${#NORDVPN_USERNAME} chars"
echo "  Bytes:  $(echo -n "$NORDVPN_USERNAME" | wc -c)"
echo "  Hex:    $(echo -n "$NORDVPN_USERNAME" | xxd -p | head -c 48)..."
echo ""
echo "Password: '${NORDVPN_PASSWORD:0:5}...'"
echo "  Length: ${#NORDVPN_PASSWORD} chars"
echo "  Bytes:  $(echo -n "$NORDVPN_PASSWORD" | wc -c)"
echo "  Hex:    $(echo -n "$NORDVPN_PASSWORD" | xxd -p | head -c 48)..."
echo ""

echo "2️⃣ CHARACTER ANALYSIS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
# Check character types
USERNAME_CHARS=$(echo -n "$NORDVPN_USERNAME" | sed 's/[a-zA-Z0-9]//g')
PASSWORD_CHARS=$(echo -n "$NORDVPN_PASSWORD" | sed 's/[a-zA-Z0-9]//g')
if [ -z "$USERNAME_CHARS" ]; then
    echo "✅ Username: Alphanumeric only (correct)"
else
    echo "⚠️  Username contains special chars: $USERNAME_CHARS"
fi
if [ -z "$PASSWORD_CHARS" ]; then
    echo "✅ Password: Alphanumeric only (correct)"
else
    echo "⚠️  Password contains special chars: $PASSWORD_CHARS"
fi
echo ""

echo "3️⃣ AUTH FILE CREATION TEST"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
AUTH_FILE="/tmp/diagnostic-auth.txt"
printf "%s\n" "$NORDVPN_USERNAME" > "$AUTH_FILE"
printf "%s\n" "$NORDVPN_PASSWORD" >> "$AUTH_FILE"
echo "Auth file created: $AUTH_FILE"
echo "  Size: $(wc -c < "$AUTH_FILE") bytes"
echo "  Lines: $(wc -l < "$AUTH_FILE")"
echo "  Hex dump:"
cat "$AUTH_FILE" | xxd -p | head -2
echo "  Content verification:"
echo "    Line 1: '$(head -1 "$AUTH_FILE" | tr -d '\n')' (${#NORDVPN_USERNAME} chars)"
echo "    Line 2: '$(tail -1 "$AUTH_FILE" | tr -d '\n')' (${#NORDVPN_PASSWORD} chars)"
echo ""

echo "4️⃣ OPENVPN CONFIG TEST"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
SERVER="uk1827.nordvpn.com"
CONFIG_URL="https://downloads.nordcdn.com/configs/files/ovpn_udp/servers/${SERVER}.udp.ovpn"
CONFIG_FILE="/tmp/diagnostic-config.ovpn"

if curl -s -f "$CONFIG_URL" -o "$CONFIG_FILE"; then
    echo "✅ Config downloaded: $(wc -l < "$CONFIG_FILE") lines"
    sed -i "s|auth-user-pass|auth-user-pass $AUTH_FILE|g" "$CONFIG_FILE"
    echo "✅ Config updated with auth file"
    echo "   Auth line in config: $(grep 'auth-user-pass' "$CONFIG_FILE")"
else
    echo "❌ Failed to download config"
    exit 1
fi
echo ""

echo "5️⃣ CONNECTION TEST"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Testing connection (this will take ~10 seconds)..."
echo ""

TIMEOUT=12
OUTPUT=$(timeout $TIMEOUT openvpn \
    --config "$CONFIG_FILE" \
    --dev null \
    --pull-filter ignore "route" \
    --pull-filter ignore "dhcp-option" \
    --verb 4 \
    --connect-timeout 10 \
    --auth-retry nointeract \
    2>&1 || true)

echo "$OUTPUT" | grep -E "(AUTH|auth|username|password|CONNECTED|failed|error|TLS|VERIFY)" | head -20
echo ""

echo "6️⃣ ANALYSIS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if echo "$OUTPUT" | grep -qi "AUTH_FAILED\|auth.*fail"; then
    echo "❌ AUTHENTICATION FAILED"
    echo ""
    echo "Possible causes:"
    echo "  1. Credentials are incorrect/expired"
    echo "  2. Credentials need email verification (go to dashboard → 'Set up NordVPN manually')"
    echo "  3. Account doesn't have Service Credentials enabled"
    echo "  4. Service Credentials were deactivated"
    echo ""
    echo "Action required:"
    echo "  → Contact NordVPN support to verify/reset Service Credentials"
    echo "  → Ensure you completed the email verification if required"
    echo "  → Verify your subscription is active"
elif echo "$OUTPUT" | grep -qi "CONNECTED\|SUCCESS"; then
    echo "✅ CONNECTION SUCCESSFUL!"
    echo "   Credentials are working correctly!"
    exit 0
else
    echo "⚠️  Connection test inconclusive"
    echo "   Check the output above for details"
fi
echo ""

echo "7️⃣ VERIFICATION CHECKLIST"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "□ Did you go through 'Set up NordVPN manually' in dashboard?"
echo "□ Did you receive and enter the email verification code?"
echo "□ Are you using Service Credentials (not account login)?"
echo "□ Is your NordVPN subscription active?"
echo "□ Have you tried contacting NordVPN support?"
echo ""


