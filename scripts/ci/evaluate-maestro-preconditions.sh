#!/bin/bash
# Evaluate Maestro preconditions and set outputs
set -e

run=false
allow_empty=false

if [[ "${EVENT_NAME}" == "workflow_dispatch" ]]; then
  run=true
  if [[ "${DISPATCH_ALLOW_EMPTY}" == "true" ]]; then
    allow_empty=true
  fi
elif [[ -n "${NORDVPN_USERNAME}" && -n "${NORDVPN_PASSWORD}" ]]; then
  run=true
fi

echo "run_maestro=${run}" >> "$GITHUB_OUTPUT"
echo "allow_empty=${allow_empty}" >> "$GITHUB_OUTPUT"
