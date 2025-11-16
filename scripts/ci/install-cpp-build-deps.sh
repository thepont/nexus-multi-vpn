#!/bin/bash
# Install C++ build dependencies for unit tests
set -e

echo "=== Installing C++ build tools ==="
sudo apt-get update -qq
sudo apt-get install -y cmake build-essential

echo "=== Installed versions ==="
cmake --version
g++ --version
