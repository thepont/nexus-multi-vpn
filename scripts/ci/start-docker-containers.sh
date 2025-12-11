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
USE_WAIT=""
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
    echo "✓ Found docker-compose command"
    # Check if --wait is supported (docker-compose v1.29.0+)
    if docker-compose --help 2>&1 | grep -q "\-\-wait"; then
        USE_WAIT="--wait"
        echo "✓ docker-compose supports --wait flag"
    else
        USE_WAIT=""
        echo "⚠️  docker-compose version doesn't support --wait, will use fixed delay"
    fi
elif docker compose version &> /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
    echo "✓ Found docker compose plugin"
    USE_WAIT="--wait"
    echo "✓ docker compose supports --wait flag"
else
    echo "⚠️  Docker Compose not found - skipping container setup"
    echo "   Tests requiring Docker will be skipped"
    exit 0
fi

echo "Using Docker Compose command: $DOCKER_COMPOSE"
echo ""
SHOULD_CLEANUP=true
if [ -z "$CI" ] && [ -z "$GITHUB_ACTIONS" ] && [ -z "$FORCE_DOCKER_CLEANUP" ]; then
    SHOULD_CLEANUP=false
fi

if [ "$SHOULD_CLEANUP" = true ]; then
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

    # Remove specific networks if they exist (by name pattern)
    # This ensures clean state without affecting other Docker networks
    for network in $(docker network ls --format "{{.Name}}" 2>/dev/null | grep -E "^e2e-routing_|^e2e-dns_|^e2e-dns-domain_|^e2e-conflict_" || true); do
        echo "Removing network: $network"
        docker network rm "$network" 2>/dev/null || true
    done
else
    echo "Skipping cleanup of existing test containers (local run detected)."
    echo "Set FORCE_DOCKER_CLEANUP=1 to force cleanup."
fi

# Avoid host port conflicts on CI runners (port 53 is reserved)
if [ -n "$CI" ] || [ -n "$GITHUB_ACTIONS" ]; then
    export DNSMASQ_HOST_PORT="${DNSMASQ_HOST_PORT:-1053}"
    echo "CI detected - using DNSMASQ_HOST_PORT=${DNSMASQ_HOST_PORT} for docker-compose.dns.yaml"
fi

echo "=== Starting OpenVPN and HTTP test containers ==="

# Function to run docker-compose with proper handling
# This function changes to the compose directory to run commands
# so that relative paths in docker-compose files work correctly
run_compose() {
    local compose_file="$1"
    local project_name="$2"
    shift 2
    local compose_args=("$@")
    
    # Add --wait flag if supported and up command is present
    if [ -n "$USE_WAIT" ]; then
        for i in "${!compose_args[@]}"; do
            if [ "${compose_args[$i]}" = "up" ]; then
                # Check if -d is present
                local has_d=false
                for arg in "${compose_args[@]}"; do
                    if [ "$arg" = "-d" ]; then
                        has_d=true
                        break
                    fi
                done
                
                # Insert --wait after -d if present, otherwise after up
                local new_args=()
                local wait_added=false
                for j in "${!compose_args[@]}"; do
                    new_args+=("${compose_args[$j]}")
                    if [ "$has_d" = true ] && [ "${compose_args[$j]}" = "-d" ] && [ "$wait_added" = false ]; then
                        new_args+=("$USE_WAIT")
                        wait_added=true
                    elif [ "$has_d" = false ] && [ "${compose_args[$j]}" = "up" ] && [ "$wait_added" = false ]; then
                        new_args+=("$USE_WAIT")
                        wait_added=true
                    fi
                done
                compose_args=("${new_args[@]}")
                break
            fi
        done
    fi
    
    if [ "$DOCKER_COMPOSE" = "docker compose" ]; then
        (cd "$COMPOSE_DIR" && docker compose -p "$project_name" -f "$(basename "$compose_file")" "${compose_args[@]}")
    else
        (cd "$COMPOSE_DIR" && docker-compose -p "$project_name" -f "$(basename "$compose_file")" "${compose_args[@]}")
    fi
}

# Start routing containers (OpenVPN UK/FR + HTTP servers)
if [ -f "$COMPOSE_DIR/docker-compose.routing.yaml" ]; then
    echo "Starting routing containers..."
    if run_compose "$COMPOSE_DIR/docker-compose.routing.yaml" "e2e-routing" up -d; then
        if [ -n "$USE_WAIT" ]; then
            echo "✅ Routing containers started and healthy"
        else
            echo "✅ Routing containers started (waiting for health checks manually)..."
            sleep 15
        fi
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
        if [ -n "$USE_WAIT" ]; then
            echo "✅ DNS containers started and healthy"
        else
            echo "✅ DNS containers started (waiting for health checks manually)..."
            sleep 15
        fi
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
        if [ -n "$USE_WAIT" ]; then
            echo "✅ DNS domain containers started and healthy"
        else
            echo "✅ DNS domain containers started (waiting for health checks manually)..."
            sleep 15
        fi
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
        if [ -n "$USE_WAIT" ]; then
            echo "✅ Conflict test containers started and healthy"
        else
            echo "✅ Conflict test containers started (waiting for health checks manually)..."
            sleep 15
        fi
    else
        echo "⚠️  Failed to start conflict containers (exit code: $?)"
    fi
else
    echo "⚠️  docker-compose.conflict.yaml not found at $COMPOSE_DIR"
fi

echo ""
echo "=== Container Status ==="
docker ps --filter "name=vpn-server" --filter "name=http-server" --filter "name=dns-server" \
  --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" || echo "Could not list containers"

# Show health status if --wait was used
if [ -n "$USE_WAIT" ]; then
    echo ""
    echo "=== Health Check Status ==="
    for project in e2e-routing e2e-dns e2e-dns-domain e2e-conflict; do
        if [ "$DOCKER_COMPOSE" = "docker compose" ]; then
            docker compose -p "$project" ps 2>/dev/null | grep -E "(NAME|healthy|unhealthy)" || true
        else
            docker-compose -p "$project" ps 2>/dev/null | grep -E "(NAME|healthy|unhealthy)" || true
        fi
    done
fi

echo ""
echo "========================================"
echo "Docker Containers Ready"
echo "========================================"
exit 0  # Always exit successfully so tests can run
