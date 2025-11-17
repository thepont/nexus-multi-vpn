#!/bin/bash
# Start Docker containers needed for E2E tests
set -e

echo "========================================"
echo "Starting Docker Containers for E2E Tests"
echo "========================================"
echo "Timestamp: $(date)"

COMPOSE_DIR="app/src/androidTest/resources/docker-compose"

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    echo "⚠️  Docker not found - skipping container setup"
    echo "   Tests requiring Docker will be skipped"
    exit 0
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "⚠️  Docker Compose not found - skipping container setup"
    echo "   Tests requiring Docker will be skipped"
    exit 0
fi

echo "=== Starting OpenVPN and HTTP test containers ==="

# Start routing containers (OpenVPN UK/FR + HTTP servers)
if [ -f "$COMPOSE_DIR/docker-compose.routing.yaml" ]; then
    echo "Starting routing containers..."
    docker-compose -f "$COMPOSE_DIR/docker-compose.routing.yaml" up -d
    echo "✅ Routing containers started"
else
    echo "⚠️  docker-compose.routing.yaml not found"
fi

# Start DNS containers
if [ -f "$COMPOSE_DIR/docker-compose.dns.yaml" ]; then
    echo "Starting DNS containers..."
    docker-compose -f "$COMPOSE_DIR/docker-compose.dns.yaml" up -d
    echo "✅ DNS containers started"
else
    echo "⚠️  docker-compose.dns.yaml not found"
fi

# Start DNS domain containers
if [ -f "$COMPOSE_DIR/docker-compose.dns-domain.yaml" ]; then
    echo "Starting DNS domain containers..."
    docker-compose -f "$COMPOSE_DIR/docker-compose.dns-domain.yaml" up -d
    echo "✅ DNS domain containers started"
else
    echo "⚠️  docker-compose.dns-domain.yaml not found"
fi

# Start conflict test containers
if [ -f "$COMPOSE_DIR/docker-compose.conflict.yaml" ]; then
    echo "Starting conflict test containers..."
    docker-compose -f "$COMPOSE_DIR/docker-compose.conflict.yaml" up -d
    echo "✅ Conflict test containers started"
else
    echo "⚠️  docker-compose.conflict.yaml not found"
fi

echo ""
echo "=== Waiting for containers to be ready ==="
sleep 10

echo ""
echo "=== Container Status ==="
docker ps --filter "name=vpn-server" --filter "name=http-server" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "========================================"
echo "Docker Containers Ready"
echo "========================================"
