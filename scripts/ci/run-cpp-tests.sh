#!/bin/bash
# Run C++ unit tests
set -e

echo "=== Running C++ unit tests ==="
cd app/src/test/cpp/build
ctest --output-on-failure --verbose
