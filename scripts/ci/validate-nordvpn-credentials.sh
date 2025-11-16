#!/bin/bash
# Validate NordVPN credentials for Maestro E2E tests
set -e

if [[ -z "${NORDVPN_USERNAME}" || -z "${NORDVPN_PASSWORD}" ]]; then
  if [[ "${ALLOW_EMPTY_CREDENTIALS}" == "true" ]]; then
    echo "⚠️  Running without NordVPN credentials (dry run)."
    echo "NORDVPN_USERNAME=dummy-user" >> "$GITHUB_ENV"
    echo "NORDVPN_PASSWORD=dummy-password" >> "$GITHUB_ENV"
  else
    echo "::error::NordVPN credentials are required for Maestro E2E tests."
    echo "       Provide NORDVPN_USERNAME and NORDVPN_PASSWORD secrets or trigger workflow_dispatch with allow-empty-credentials."
    exit 1
  fi
fi
