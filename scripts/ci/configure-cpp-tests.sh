#!/bin/bash
# Configure C++ unit tests
set -e

echo "=== Configuring C++ unit tests ==="
cd app/src/test/cpp
mkdir -p build
cd build
cmake ..
