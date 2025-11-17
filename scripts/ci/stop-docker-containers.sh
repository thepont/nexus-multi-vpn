#!/bin/bash
# Stop Docker containers after E2E tests
set -e

echo "========================================"
echo "Stopping Docker Containers"
echo "========================================"
echo "Timestamp: $(date)"

COMPOSE_DIR="app/src/androidTest/resources/docker-compose"

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    echo "Docker not found - nothing to stop"
    exit 0
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "Docker Compose not found - nothing to stop"
    exit 0
fi

echo "=== Stopping test containers ==="

# Stop all docker-compose files
for compose_file in "$COMPOSE_DIR"/*.yaml; do
    if [ -f "$compose_file" ]; then
        echo "Stopping $(basename $compose_file)..."
        docker-compose -f "$compose_file" down || true
    fi
done

echo ""
echo "========================================"
echo "Docker Containers Stopped"
echo "========================================"
