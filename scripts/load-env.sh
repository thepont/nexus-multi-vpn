#!/bin/bash
# Script to load .env file and export variables
# Usage: source scripts/load-env.sh

if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
    echo "✓ Loaded environment variables from .env"
else
    echo "⚠️  Warning: .env file not found. Using .env.example as template."
    if [ -f .env.example ]; then
        echo "   Create .env file from .env.example and fill in your credentials."
    fi
fi


