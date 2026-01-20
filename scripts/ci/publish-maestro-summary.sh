#!/bin/bash
# Publish Maestro test summary to GitHub step summary
set -e

if [[ "${RUN_MAESTRO}" == "true" ]]; then
  {
    echo "## ✅ Maestro E2E Tests - Will Run"
    echo ""
    if [[ "${ALLOW_EMPTY}" == "true" ]]; then
      echo "**Mode:** Manual workflow dispatch with empty credentials allowed"
      echo "- Running with dummy/test credentials"
      echo "- Some tests may skip credential-dependent operations"
    else
      echo "**Mode:** Full E2E testing with real credentials"
      echo "- Using provided NordVPN credentials"
      echo "- All credential-dependent tests will run"
    fi
    echo ""
    echo "**Test Scope:** Phone UI tests only (TV tests excluded)"
  } >> "$GITHUB_STEP_SUMMARY"
else
  {
    echo "## ⚠️ Maestro E2E Tests - Skipped"
    echo ""
    echo "**Reason:** NordVPN credentials not provided"
    echo ""
    echo "**To run these tests:**"
    echo "1. Add secrets: \`NORDVPN_USERNAME\` and \`NORDVPN_PASSWORD\`"
    echo "2. OR use manual workflow_dispatch with \`allow-empty-credentials: true\`"
    echo ""
    echo "**Note:** Tests are skipped, not failed. Other CI jobs will continue."
  } >> "$GITHUB_STEP_SUMMARY"
fi
