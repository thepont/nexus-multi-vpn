#!/bin/bash

set -e

echo "═══════════════════════════════════════════════════════"
echo "🧪 Testing NordVPN Service Credentials"
echo "═══════════════════════════════════════════════════════"

# Check if credentials are provided
if [ -z "$NORDVPN_USERNAME" ] || [ -z "$NORDVPN_PASSWORD" ]; then
    echo "❌ Error: NORDVPN_USERNAME and NORDVPN_PASSWORD must be set"
    echo "   Usage: docker run -e NORDVPN_USERNAME=... -e NORDVPN_PASSWORD=... ..."
    exit 1
fi

echo "✅ Credentials provided"
echo "   Username length: ${#NORDVPN_USERNAME} chars"
echo "   Password length: ${#NORDVPN_PASSWORD} chars"
echo ""
echo "🔍 Credential inspection:"
echo "   Username (first 10 chars): ${NORDVPN_USERNAME:0:10}..."
echo "   Username (last 10 chars): ...${NORDVPN_USERNAME: -10}"
echo "   Username hex (first 20 bytes): $(echo -n "$NORDVPN_USERNAME" | head -c 20 | xxd -p | tr -d '\n')"
echo "   Password length check: $(echo -n "$NORDVPN_PASSWORD" | wc -c) bytes"
echo ""
echo "🔍 Checking for common issues:"
# Trim credentials for checking (don't modify originals yet)
TRIMMED_USERNAME=$(echo -n "$NORDVPN_USERNAME" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | tr -d '\r\n')
TRIMMED_PASSWORD=$(echo -n "$NORDVPN_PASSWORD" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | tr -d '\r\n')

# Check for whitespace
if [[ "$NORDVPN_USERNAME" =~ ^[[:space:]] ]] || [[ "$NORDVPN_USERNAME" =~ [[:space:]]$ ]]; then
    echo "   ⚠️  Username has leading/trailing whitespace (will be trimmed)"
fi
if [[ "$NORDVPN_PASSWORD" =~ ^[[:space:]] ]] || [[ "$NORDVPN_PASSWORD" =~ [[:space:]]$ ]]; then
    echo "   ⚠️  Password has leading/trailing whitespace (will be trimmed)"
fi
# Check for embedded newlines (not just at end)
if echo -n "$NORDVPN_USERNAME" | grep -q $'\n'; then
    echo "   ⚠️  Username contains embedded newlines (will be removed)"
fi
if echo -n "$NORDVPN_PASSWORD" | grep -q $'\n'; then
    echo "   ⚠️  Password contains embedded newlines (will be removed)"
fi
# Check trimmed lengths
if [ ${#TRIMMED_USERNAME} -ne ${#NORDVPN_USERNAME} ]; then
    echo "   ⚠️  Username will be trimmed: ${#NORDVPN_USERNAME} -> ${#TRIMMED_USERNAME} chars"
fi
if [ ${#TRIMMED_PASSWORD} -ne ${#NORDVPN_PASSWORD} ]; then
    echo "   ⚠️  Password will be trimmed: ${#NORDVPN_PASSWORD} -> ${#TRIMMED_PASSWORD} chars"
fi
# Final trimmed lengths
echo "   ✅ Trimmed username length: ${#TRIMMED_USERNAME} chars"
echo "   ✅ Trimmed password length: ${#TRIMMED_PASSWORD} chars"

# Test server - try UDP first, fallback to TCP if needed
# Also try different servers if one fails
SERVER_HOSTNAME="${TEST_SERVER:-uk1827.nordvpn.com}"
PROTOCOL="${TEST_PROTOCOL:-udp}"  # udp or tcp
CONFIG_URL="https://downloads.nordcdn.com/configs/files/ovpn_${PROTOCOL}/servers/${SERVER_HOSTNAME}.${PROTOCOL}.ovpn"

echo "Testing server: ${SERVER_HOSTNAME} (${PROTOCOL})"

echo ""
echo "📡 Downloading OpenVPN config for ${SERVER_HOSTNAME}..."

# Download config
CONFIG_FILE="/tmp/nordvpn.ovpn"
if ! curl -s -f "$CONFIG_URL" -o "$CONFIG_FILE"; then
    echo "❌ Failed to download config from ${CONFIG_URL}"
    exit 1
fi

echo "✅ Config downloaded ($(wc -l < "$CONFIG_FILE" | tr -d ' ') lines)"

# Modify config for testing:
# 1. Remove unsupported options (for OpenVPN 2.x compatibility)
# 2. Set auth-user-pass to use our credentials
# Note: OpenVPN 2.x doesn't need client-cert-not-required if the server doesn't require it

MODIFIED_CONFIG="/tmp/nordvpn-test.ovpn"
cat "$CONFIG_FILE" | \
    grep -v "ping-timer-rem" | \
    grep -v "remote-random" | \
    grep -v "fast-io" | \
    grep -v "^comp-lzo" \
    > "$MODIFIED_CONFIG"

# Create auth file
# Properly handle credentials - trim whitespace and remove all newlines/carriage returns
AUTH_FILE="/tmp/auth.txt"
# Use echo -n to avoid adding newline, then explicitly add one newline per line
# This ensures exactly: username\npassword\n (no extra newlines)
echo -n "$NORDVPN_USERNAME" | sed 's/\r//g' | sed 's/\n//g' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | head -c 100 > "$AUTH_FILE"
echo "" >> "$AUTH_FILE"  # Add single newline
echo -n "$NORDVPN_PASSWORD" | sed 's/\r//g' | sed 's/\n//g' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | head -c 100 >> "$AUTH_FILE"
echo "" >> "$AUTH_FILE"  # Add single newline
chmod 600 "$AUTH_FILE"

echo ""
echo "📄 Auth file verification:"
echo "   Auth file lines: $(wc -l < "$AUTH_FILE" | tr -d ' ')"
echo "   Username in file: $(head -1 "$AUTH_FILE" | wc -c) bytes"
echo "   Password in file: $(tail -1 "$AUTH_FILE" | wc -c) bytes"
echo "   Username (first 5 chars): $(head -1 "$AUTH_FILE" | head -c 5)"
echo "   Password (first 5 chars): $(tail -1 "$AUTH_FILE" | head -c 5)"

# Update config to use auth file
sed -i "s|auth-user-pass|auth-user-pass $AUTH_FILE|g" "$MODIFIED_CONFIG"

echo ""
echo "🔧 Config prepared:"
echo "   - auth-user-pass: $(grep -c 'auth-user-pass' "$MODIFIED_CONFIG" || echo 0)"
echo "   - auth file: $AUTH_FILE ($(wc -l < "$AUTH_FILE" | tr -d ' ') lines)"
echo "   - config lines: $(wc -l < "$MODIFIED_CONFIG" | tr -d ' ')"

echo ""
echo "═══════════════════════════════════════════════════════"
echo "🔌 Attempting OpenVPN connection (10 second timeout)..."
echo "═══════════════════════════════════════════════════════"

# Try to connect with a timeout
# We use --dev null to create a dummy interface (won't affect host)
# We use --pull-filter to ignore routes/dns (won't affect host network)
TIMEOUT=10
TIMEOUT_CMD="timeout $TIMEOUT"

if ! command -v timeout &> /dev/null; then
    # Alpine doesn't have timeout by default, use a different approach
    TIMEOUT_CMD=""
fi

set +e  # Don't exit on error, we want to capture the output

        if [ -n "$TIMEOUT_CMD" ]; then
            OUTPUT=$($TIMEOUT_CMD openvpn \
                --config "$MODIFIED_CONFIG" \
                --dev null \
                --pull-filter ignore "route" \
                --pull-filter ignore "dhcp-option" \
                --verb 4 \
                --connect-retry 1 \
                --connect-timeout 10 \
                --auth-retry nointeract \
                2>&1)
            EXIT_CODE=$?
        else
            # Fallback: run in background and kill after timeout
            openvpn \
                --config "$MODIFIED_CONFIG" \
                --dev null \
                --pull-filter ignore "route" \
                --pull-filter ignore "dhcp-option" \
                --verb 4 \
                --connect-retry 1 \
                --connect-timeout 10 \
                --auth-retry nointeract \
                > /tmp/openvpn.log 2>&1 &
            OVPN_PID=$!
            sleep $TIMEOUT
            kill $OVPN_PID 2>/dev/null
            wait $OVPN_PID 2>/dev/null
            EXIT_CODE=$?
            OUTPUT=$(cat /tmp/openvpn.log)
        fi

echo "$OUTPUT"

echo ""
echo "═══════════════════════════════════════════════════════"
echo "📋 Analysis"
echo "═══════════════════════════════════════════════════════"

# Check for common errors
if echo "$OUTPUT" | grep -qi "AUTH_FAILED\|auth.*fail\|username.*password.*incorrect\|authentication.*fail"; then
    echo "❌ AUTHENTICATION FAILED"
    echo ""
    echo "This means:"
    echo "  1. The credentials are incorrect"
    echo "  2. The credentials are not Service Credentials (might be account credentials)"
    echo "  3. The credentials have expired"
    echo ""
    echo "To verify:"
    echo "  - Check NordVPN dashboard: https://my.nordaccount.com/dashboard/nordvpn/"
    echo "  - Look for 'Service Credentials' (not regular account credentials)"
    echo "  - Service Credentials are specifically for OpenVPN/IKEv2"
elif echo "$OUTPUT" | grep -qi "RESOLVE.*fail\|could not resolve\|network.*unreachable"; then
    echo "❌ NETWORK/CONNECTION ERROR"
    echo "   Cannot reach NordVPN server (DNS or network issue)"
elif echo "$OUTPUT" | grep -qi "TLS.*error\|certificate\|SSL"; then
    echo "❌ TLS/CERTIFICATE ERROR"
    echo "   There's an issue with the TLS handshake"
elif echo "$OUTPUT" | grep -qi "CONNECTED\|SUCCESS\|Initialization Sequence Completed"; then
    echo "✅ CONNECTION SUCCESSFUL!"
    echo "   Credentials are valid and connection works"
    exit 0
elif [ $EXIT_CODE -eq 124 ] || echo "$OUTPUT" | grep -qi "timeout"; then
    echo "⏱️  TIMEOUT"
    echo "   Connection attempt timed out (this might indicate authentication is working but slow)"
    echo "   Check the output above for AUTH_FAILED or CONNECTED messages"
else
    echo "⚠️  UNKNOWN ERROR (exit code: $EXIT_CODE)"
    echo "   Check the output above for details"
fi

echo ""
echo "Full output saved above for analysis"
exit $EXIT_CODE

