#!/bin/bash
# Publish Maestro test summary to GitHub step summary
set -e

if [[ "${RUN_MAESTRO}" == "true" ]]; then
  {
    echo "## Maestro E2E Tests"
    if [[ "${ALLOW_EMPTY}" == "true" ]]; then
      echo "- Running with dummy credentials (workflow_dispatch override)."
    else
      echo "- Running with provided NordVPN credentials."
    fi
  } >> "$GITHUB_STEP_SUMMARY"
else
  {
    echo "## Maestro E2E Tests"
    echo "- Skipped because NordVPN credentials were not provided and workflow_dispatch override not enabled."
  } >> "$GITHUB_STEP_SUMMARY"
fi
