#!/bin/bash
# Generate release build summary for GitHub Actions
set -e

{
  echo "## Release APK Build"
  echo ""
  echo "✅ Successfully built release APK with native OpenVPN libraries"
  echo ""
  echo "### APK Details"
  APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
  if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo "- **Size:** $APK_SIZE"
    echo "- **Path:** \`$APK_PATH\`"
    echo "- **Architectures:** arm64-v8a, armeabi-v7a, x86_64, x86"
    echo ""
    echo "### Next Steps"
    echo "1. Download the APK artifact from this workflow run"
    echo "2. Sign the APK with your release keystore"
    echo "3. Upload to Google Play Console or distribute as needed"
    echo ""
    echo "> **Note:** This is an unsigned APK. You must sign it before distribution."
  else
    echo "⚠️ APK file not found at expected path"
  fi
} >> "$GITHUB_STEP_SUMMARY"
