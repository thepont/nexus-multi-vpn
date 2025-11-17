#!/bin/bash
# Build C++ unit tests
set -e

echo "=== Building C++ unit tests ==="
cd app/src/test/cpp/build
make -j4
