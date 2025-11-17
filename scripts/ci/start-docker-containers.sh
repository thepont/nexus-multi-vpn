#!/bin/bash
# Start Docker containers needed for E2E tests
# Note: We don't use 'set -e' here to handle errors gracefully
set +e  # Allow script to continue on errors

echo "========================================"
echo "Starting Docker Containers for E2E Tests"
echo "========================================"
echo "Timestamp: $(date)"

# Save current directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_DIR="$PROJECT_DIR/app/src/androidTest/resources/docker-compose"

echo "Project directory: $PROJECT_DIR"
echo "Compose directory: $COMPOSE_DIR"

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    echo "⚠️  Docker not found - skipping container setup"
    echo "   Tests requiring Docker will be skipped"
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
    echo "⚠️  Docker Compose not found - skipping container setup"
    echo "   Tests requiring Docker will be skipped"
    exit 0
fi

echo "Using Docker Compose command: $DOCKER_COMPOSE"
echo ""
echo "=== Cleaning up old containers first ==="

# Function to stop and remove containers for a project
cleanup_project() {
    local project_name="$1"
    if [ "$DOCKER_COMPOSE" = "docker compose" ]; then
        (cd "$COMPOSE_DIR" && docker compose -p "$project_name" down --remove-orphans 2>/dev/null || true)
    else
        (cd "$COMPOSE_DIR" && docker-compose -p "$project_name" down --remove-orphans 2>/dev/null || true)
    fi
}

# Clean up any existing containers with our project names
cleanup_project "e2e-routing"
cleanup_project "e2e-dns"
cleanup_project "e2e-dns-domain"
cleanup_project "e2e-conflict"

echo "=== Starting OpenVPN and HTTP test containers ==="

# Function to run docker-compose with proper handling
# This function changes to the compose directory to run commands
# so that relative paths in docker-compose files work correctly
run_compose() {
    local compose_file="$1"
    local project_name="$2"
    shift 2
    if [ "$DOCKER_COMPOSE" = "docker compose" ]; then
        (cd "$COMPOSE_DIR" && docker compose -p "$project_name" -f "$(basename "$compose_file")" "$@")
    else
        (cd "$COMPOSE_DIR" && docker-compose -p "$project_name" -f "$(basename "$compose_file")" "$@")
    fi
}

# Start routing containers (OpenVPN UK/FR + HTTP servers)
if [ -f "$COMPOSE_DIR/docker-compose.routing.yaml" ]; then
    echo "Starting routing containers..."
    if run_compose "$COMPOSE_DIR/docker-compose.routing.yaml" "e2e-routing" up -d; then
        echo "✅ Routing containers started"
    else
        echo "⚠️  Failed to start routing containers (exit code: $?)"
    fi
else
    echo "⚠️  docker-compose.routing.yaml not found at $COMPOSE_DIR"
fi

# Start DNS containers
if [ -f "$COMPOSE_DIR/docker-compose.dns.yaml" ]; then
    echo "Starting DNS containers..."
    if run_compose "$COMPOSE_DIR/docker-compose.dns.yaml" "e2e-dns" up -d; then
        echo "✅ DNS containers started"
    else
        echo "⚠️  Failed to start DNS containers (exit code: $?)"
    fi
else
    echo "⚠️  docker-compose.dns.yaml not found at $COMPOSE_DIR"
fi

# Start DNS domain containers
if [ -f "$COMPOSE_DIR/docker-compose.dns-domain.yaml" ]; then
    echo "Starting DNS domain containers..."
    if run_compose "$COMPOSE_DIR/docker-compose.dns-domain.yaml" "e2e-dns-domain" up -d; then
        echo "✅ DNS domain containers started"
    else
        echo "⚠️  Failed to start DNS domain containers (exit code: $?)"
    fi
else
    echo "⚠️  docker-compose.dns-domain.yaml not found at $COMPOSE_DIR"
fi

# Start conflict test containers
if [ -f "$COMPOSE_DIR/docker-compose.conflict.yaml" ]; then
    echo "Starting conflict test containers..."
    if run_compose "$COMPOSE_DIR/docker-compose.conflict.yaml" "e2e-conflict" up -d; then
        echo "✅ Conflict test containers started"
    else
        echo "⚠️  Failed to start conflict containers (exit code: $?)"
    fi
else
    echo "⚠️  docker-compose.conflict.yaml not found at $COMPOSE_DIR"
fi

echo ""
echo "=== Waiting for containers to be ready ==="
sleep 10

echo ""
echo "=== Container Status ==="
docker ps --filter "name=vpn-server" --filter "name=http-server" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" || echo "Could not list containers"

echo ""
echo "========================================"
echo "Docker Containers Ready"
echo "========================================"
exit 0  # Always exit successfully so tests can run
