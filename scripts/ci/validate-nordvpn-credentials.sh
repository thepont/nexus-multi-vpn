#!/bin/bash
# Validate NordVPN credentials for Maestro E2E tests
set -e

echo "=== Validating NordVPN Credentials ==="

if [[ -z "${NORDVPN_USERNAME}" || -z "${NORDVPN_PASSWORD}" ]]; then
  if [[ "${ALLOW_EMPTY_CREDENTIALS}" == "true" ]]; then
    echo "⚠️  Mode: DRY RUN (no credentials)"
    echo ""
    echo "Using dummy credentials for testing:"
    echo "  - Username: dummy-user"
    echo "  - Password: ********"
    echo ""
    echo "Note: Tests requiring real VPN connections will fail or skip"
    echo "NORDVPN_USERNAME=dummy-user" >> "$GITHUB_ENV"
    echo "NORDVPN_PASSWORD=dummy-password" >> "$GITHUB_ENV"
  else
    echo "❌ ERROR: NordVPN credentials are missing"
    echo ""
    echo "Maestro E2E tests require NordVPN credentials to run."
    echo ""
    echo "To fix this:"
    echo "  1. Add repository secrets:"
    echo "     - NORDVPN_USERNAME"
    echo "     - NORDVPN_PASSWORD"
    echo ""
    echo "  2. OR trigger workflow_dispatch with:"
    echo "     - allow-empty-credentials: true (for dry run)"
    echo ""
    echo "::error::NordVPN credentials are required for Maestro E2E tests."
    exit 1
  fi
else
  echo "✅ Credentials validated"
  echo "  - Username: ${NORDVPN_USERNAME:0:3}***"
  echo "  - Password: ********"
  echo ""
  echo "Mode: FULL E2E testing with real credentials"
fi
