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

# Determine which docker-compose command to use
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
elif docker compose version &> /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    echo "Docker Compose not found - nothing to stop"
    exit 0
fi

echo "Using Docker Compose command: $DOCKER_COMPOSE"
echo ""
echo "=== Stopping test containers ==="

# Stop all docker-compose files
for compose_file in "$COMPOSE_DIR"/*.yaml; do
    if [ -f "$compose_file" ]; then
        echo "Stopping $(basename $compose_file)..."
        $DOCKER_COMPOSE -f "$compose_file" down || true
    fi
done

echo ""
echo "========================================"
echo "Docker Containers Stopped"
echo "========================================"
