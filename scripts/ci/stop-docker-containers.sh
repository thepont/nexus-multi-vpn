#!/bin/bash
# Stop Docker containers after E2E tests
# Note: We don't use 'set -e' here to handle errors gracefully
set +e  # Allow script to continue on errors

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
DOCKER_COMPOSE=""
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
    echo "✓ Found docker-compose command"
elif docker compose version &> /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
    echo "✓ Found docker compose plugin"
else
    echo "Docker Compose not found - nothing to stop"
    exit 0
fi

echo "Using Docker Compose command: $DOCKER_COMPOSE"
echo ""
echo "=== Stopping test containers ==="

# Function to run docker-compose with proper handling
run_compose() {
    if [ "$DOCKER_COMPOSE" = "docker compose" ]; then
        docker compose "$@"
    else
        docker-compose "$@"
    fi
}

# Stop all docker-compose files
for compose_file in "$COMPOSE_DIR"/*.yaml; do
    if [ -f "$compose_file" ]; then
        echo "Stopping $(basename $compose_file)..."
        run_compose -f "$compose_file" down || echo "⚠️  Could not stop $(basename $compose_file)"
    fi
done

echo ""
echo "========================================"
echo "Docker Containers Stopped"
echo "========================================"
exit 0  # Always exit successfully
