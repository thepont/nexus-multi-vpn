#!/bin/bash
# Evaluate Maestro preconditions and set outputs
set -e

echo "=== Evaluating Maestro Test Preconditions ==="
echo "Event: ${EVENT_NAME}"
echo "Dispatch allow empty: ${DISPATCH_ALLOW_EMPTY}"
echo "NordVPN username present: $([ -n "${NORDVPN_USERNAME}" ] && echo "yes" || echo "no")"
echo "NordVPN password present: $([ -n "${NORDVPN_PASSWORD}" ] && echo "yes" || echo "no")"

run=false
allow_empty=false

if [[ "${EVENT_NAME}" == "workflow_dispatch" ]]; then
  echo "✓ Workflow dispatch event - tests will run"
  run=true
  if [[ "${DISPATCH_ALLOW_EMPTY}" == "true" ]]; then
    echo "✓ Empty credentials allowed for this manual run"
    allow_empty=true
  fi
elif [[ -n "${NORDVPN_USERNAME}" && -n "${NORDVPN_PASSWORD}" ]]; then
  echo "✓ NordVPN credentials found - tests will run"
  run=true
else
  echo "⚠️  No NordVPN credentials - Maestro tests will be skipped"
  echo "    To run tests manually: workflow_dispatch with allow-empty-credentials"
fi

echo ""
echo "=== Decision ==="
echo "Run Maestro: ${run}"
echo "Allow empty credentials: ${allow_empty}"

echo "run_maestro=${run}" >> "$GITHUB_OUTPUT"
echo "allow_empty=${allow_empty}" >> "$GITHUB_OUTPUT"
