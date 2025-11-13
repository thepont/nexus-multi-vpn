#!/bin/bash
# Quick script to verify .env credentials are formatted correctly

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [ ! -f "$PROJECT_ROOT/.env" ]; then
    echo "❌ .env file not found at $PROJECT_ROOT/.env"
    exit 1
fi

echo "📁 Checking .env file format..."
echo ""

# Check if variables exist
if grep -q "^NORDVPN_USERNAME=" "$PROJECT_ROOT/.env"; then
    USERNAME=$(grep "^NORDVPN_USERNAME=" "$PROJECT_ROOT/.env" | cut -d'=' -f2- | sed 's/^["'\'']//;s/["'\'']$//')
    echo "✅ NORDVPN_USERNAME found"
    echo "   Length: ${#USERNAME} chars"
    echo "   First 10: ${USERNAME:0:10}..."
    echo "   Last 10: ...${USERNAME: -10}"
    if [[ "$USERNAME" =~ [[:space:]] ]]; then
        echo "   ⚠️  Contains whitespace!"
    fi
else
    echo "❌ NORDVPN_USERNAME not found in .env"
fi

echo ""

if grep -q "^NORDVPN_PASSWORD=" "$PROJECT_ROOT/.env"; then
    PASSWORD=$(grep "^NORDVPN_PASSWORD=" "$PROJECT_ROOT/.env" | cut -d'=' -f2- | sed 's/^["'\'']//;s/["'\'']$//')
    echo "✅ NORDVPN_PASSWORD found"
    echo "   Length: ${#PASSWORD} chars"
    echo "   First 5: ${PASSWORD:0:5}..."
    if [[ "$PASSWORD" =~ [[:space:]] ]]; then
        echo "   ⚠️  Contains whitespace!"
    fi
else
    echo "❌ NORDVPN_PASSWORD not found in .env"
fi

echo ""
echo "💡 Tip: If credentials are wrong, regenerate them at:"
echo "   https://my.nordaccount.com/dashboard/nordvpn/"
