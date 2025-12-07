#!/bin/bash
# Validate NordVPN credentials for tests that hit live NordVPN tunnels
set -e

if [[ -z "${NORDVPN_USERNAME}" || -z "${NORDVPN_PASSWORD}" ]]; then
  if [[ "${ALLOW_EMPTY_CREDENTIALS}" == "true" ]]; then
    echo "⚠️  Running without NordVPN credentials (dry run)."
    echo "NORDVPN_USERNAME=dummy-user" >> "$GITHUB_ENV"
    echo "NORDVPN_PASSWORD=dummy-password" >> "$GITHUB_ENV"
  else
    echo "::error::NordVPN credentials (NORDVPN_USERNAME / NORDVPN_PASSWORD) are required for DiagnosticRoutingTest and other live VPN tests."
    echo "       Add these as repository secrets or trigger workflow_dispatch with ALLOW_EMPTY_CREDENTIALS=true to bypass."
    exit 1
  fi
fi
