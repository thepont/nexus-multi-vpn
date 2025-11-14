#!/usr/bin/env bash
set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PACKAGE_NAME="com.multiregionvpn"
APP_ID="$PACKAGE_NAME"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

COMPOSE_FILE_ROUTING="$PROJECT_DIR/app/src/androidTest/resources/docker-compose/docker-compose.routing.yaml"
DOCKER_COMPOSE_CMD=""
SKIP_DOCKER=false

trap_cleanup() {
    if [ -n "$DOCKER_COMPOSE_CMD" ] && [ -f "$COMPOSE_FILE_ROUTING" ] && [ "$SKIP_DOCKER" = false ]; then
        log_info "Stopping Docker test stack..."
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE_ROUTING" down >/dev/null 2>&1 || true
    fi
}
trap trap_cleanup EXIT

# Functions
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

log_step() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}📋 $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
}

# Uninstall existing app builds to ensure clean state
cleanup_installed_apps() {
    log_step "Cleaning Up Previous Installations"
    for pkg in \
        "com.multiregionvpn" \
        "com.example.diagnostic.uk" \
        "com.example.diagnostic.fr" \
        "com.example.diagnostic.direct"; do
        log_info "Uninstalling $pkg (if present)..."
        adb uninstall "$pkg" >/dev/null 2>&1 && log_success "Removed $pkg" || log_info "$pkg not installed"
    done
}

# Check prerequisites
check_prerequisites() {
    log_step "Checking Prerequisites"
    
    # Check if adb is available
    if ! command -v adb &> /dev/null; then
        log_error "adb not found. Please install Android SDK Platform Tools."
        exit 1
    fi
    log_success "adb found: $(adb version | head -1)"
    
    # Check if device is connected
    if ! adb devices | grep -q "device$"; then
        log_error "No Android device/emulator connected."
        log_info "Please connect a device or start an emulator:"
        log_info "  emulator -avd <avd_name>"
        exit 1
    fi
    log_success "Android device/emulator connected"
    wait_for_device_with_timeout
    
    # Check if .env file exists
    if [ ! -f "$PROJECT_DIR/.env" ]; then
        log_warning ".env file not found. Creating template..."
        cat > "$PROJECT_DIR/.env" << EOF
# NordVPN Credentials for E2E Tests
# Get these from your NordVPN account
NORDVPN_USERNAME=your_username_here
NORDVPN_PASSWORD=your_password_here
EOF
        log_error "Please edit .env and add your NordVPN credentials"
        exit 1
    fi
    log_success ".env file found"
    
    # Check if credentials are set
    source "$PROJECT_DIR/.env"
    if [ -z "$NORDVPN_USERNAME" ] || [ "$NORDVPN_USERNAME" = "your_username_here" ]; then
        log_error "NORDVPN_USERNAME not set in .env file"
        exit 1
    fi
    if [ -z "$NORDVPN_PASSWORD" ] || [ "$NORDVPN_PASSWORD" = "your_password_here" ]; then
        log_error "NORDVPN_PASSWORD not set in .env file"
        exit 1
    fi
    log_success "NordVPN credentials loaded from .env"
}

# Setup environment variables
setup_environment() {
    log_step "Setting Up Environment Variables"
    
    # Set Android SDK paths
    if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
        # Try to find Android SDK in common locations
        if [ -d "$HOME/Android/Sdk" ]; then
            export ANDROID_HOME="$HOME/Android/Sdk"
            export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
            log_info "Set ANDROID_HOME to $ANDROID_HOME"
        else
            log_warning "ANDROID_HOME not set and not found in common location"
        fi
    fi
    
    # Set NDK path if available
    if [ -z "$ANDROID_NDK" ] && [ -n "$ANDROID_HOME" ]; then
        # Find the latest NDK version
        NDK_PATH=$(find "$ANDROID_HOME/ndk" -maxdepth 1 -type d -name "*.*" 2>/dev/null | sort -V | tail -1)
        if [ -n "$NDK_PATH" ]; then
            export ANDROID_NDK="$NDK_PATH"
            log_info "Set ANDROID_NDK to $ANDROID_NDK"
        fi
    fi
    
    # Set vcpkg if available
    if [ -z "$VCPKG_ROOT" ] && [ -d "$HOME/vcpkg" ]; then
        export VCPKG_ROOT="$HOME/vcpkg"
        log_info "Set VCPKG_ROOT to $VCPKG_ROOT"
    fi
    
    # Set JAVA_HOME if not set
    if [ -z "$JAVA_HOME" ]; then
        # Try to find Java 17
        JAVA_17=$(find /usr/lib/jvm -name "java-17*" -type d 2>/dev/null | head -1)
        if [ -n "$JAVA_17" ]; then
            export JAVA_HOME="$JAVA_17"
            log_info "Set JAVA_HOME to $JAVA_HOME"
        fi
    fi
    
    # Add Java to PATH if JAVA_HOME is set
    if [ -n "$JAVA_HOME" ]; then
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
    
    log_success "Environment variables configured"
}

# Install app
install_app() {
    log_step "Installing Application"
    
    cd "$PROJECT_DIR"
    
    log_info "Building and installing debug APK..."
    if ./gradlew :app:installDebug; then
        log_success "App installed successfully"
    else
        log_error "Failed to install app"
        exit 1
    fi
}

# Grant permissions
grant_permissions() {
    log_step "Granting Permissions"
    
    # Grant runtime permissions via GrantPermissionRule (handled in test)
    log_info "Runtime permissions (INTERNET, ACCESS_NETWORK_STATE) will be granted by GrantPermissionRule"
    
    # Pre-approve VPN permission using appops (REQUIRED for VPN)
    log_info "Pre-approving VPN permission via appops..."
    if adb shell appops set "$PACKAGE_NAME" ACTIVATE_VPN allow 2>/dev/null; then
        log_success "VPN permission pre-approved (ACTIVATE_VPN allow)"
    else
        log_warning "Could not pre-approve VPN permission via appops"
        log_warning "The test will try to handle the permission dialog, but may fail"
    fi
    
    # Verify VPN permission
    log_info "Verifying VPN permission..."
    PREPARE_RESULT=$(adb shell "su -c 'am start -a android.net.VpnService.prepare -n $PACKAGE_NAME/.ui.MainActivity' 2>&1" || echo "error")
    if echo "$PREPARE_RESULT" | grep -q "Error"; then
        log_warning "VPN permission verification inconclusive (this is OK if appops worked)"
    else
        log_success "VPN permission appears to be granted"
    fi
}


# Ensure device owner provisioning for debug builds
ensure_device_owner() {
    log_step "Ensuring Device Owner Provisioning"

    local owner_status
    owner_status=$(adb shell dpm list device-owner 2>/dev/null || true)

    if echo "$owner_status" | grep -q "$PACKAGE_NAME"; then
        log_success "${PACKAGE_NAME} is already device owner"
        return
    fi

    log_info "Attempting to promote ${PACKAGE_NAME} to device owner (requires freshly wiped emulator with no accounts)."
    local command_output
    if command_output=$(adb shell dpm set-device-owner "${PACKAGE_NAME}/.deviceowner.TestDeviceOwnerReceiver" 2>&1); then
        log_success "Device owner set successfully"
    else
        log_error "Failed to set device owner"
        echo "$command_output"
        log_warning "If this fails, wipe the emulator data (emulator -avd <name> -wipe-data) and retry."
        exit 1
    fi
}

wait_for_device_with_timeout() {
    log_info "Waiting for emulator/device to come online (60s timeout)..."
    if timeout 60 adb wait-for-device; then
        log_success "Device detected."
    else
        log_error "Device did not appear within 60 seconds."
        exit 1
    fi
}

detect_docker_compose() {
    if command -v docker-compose >/dev/null 2>&1; then
        DOCKER_COMPOSE_CMD="docker-compose"
    elif command -v docker >/dev/null 2>&1; then
        DOCKER_COMPOSE_CMD="docker compose"
    else
        DOCKER_COMPOSE_CMD=""
    fi
}

start_docker_stack() {
    if [ "$SKIP_DOCKER" = true ]; then
        log_info "Skipping Docker stack startup (--skip-docker)"
        return
    fi
    log_step "Starting Docker Test Stack"

    if ! command -v docker >/dev/null 2>&1; then
        log_warning "Docker is not installed; skipping local stack startup."
        return
    fi

    detect_docker_compose
    if [ -z "$DOCKER_COMPOSE_CMD" ]; then
        log_warning "docker compose command not found; skipping local stack startup."
        return
    fi

    if [ ! -f "$COMPOSE_FILE_ROUTING" ]; then
        log_warning "Compose file not found at $COMPOSE_FILE_ROUTING"
        return
    fi

    log_info "Using compose command: $DOCKER_COMPOSE_CMD"
    log_info "Bringing up stack from $COMPOSE_FILE_ROUTING"
    if $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE_ROUTING" up -d --build; then
        log_success "Docker services started"
    else
        log_warning "Failed to start Docker services"
    fi
}

stop_docker_stack() {
    if [ "$SKIP_DOCKER" = true ]; then
        return
    fi
    if [ -n "$DOCKER_COMPOSE_CMD" ] && [ -f "$COMPOSE_FILE_ROUTING" ]; then
        log_step "Stopping Docker Test Stack"
        $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE_ROUTING" down || true
    fi
}

# Clear app data (optional, for clean test state)
clear_app_data() {
    if [ "${CLEAR_APP_DATA:-false}" = "true" ]; then
        log_step "Clearing App Data"
        log_info "Clearing app data for clean test state..."
        adb shell pm clear "$PACKAGE_NAME" 2>/dev/null || true
        log_success "App data cleared"
    else
        log_info "Skipping app data clearing (set CLEAR_APP_DATA=true to enable)"
    fi
}

# Run tests
run_tests() {
    log_step "Running E2E Tests"
    
    cd "$PROJECT_DIR"
    
    # Source .env for credentials
    source .env
    
    # Determine which test to run
    TEST_CLASS="${1:-com.multiregionvpn.BasicConnectionTest}"
    TEST_METHOD="${2:-}"
    
    if [ -n "$TEST_METHOD" ]; then
        TEST_ARG="-Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASS}#${TEST_METHOD}"
        log_info "Running single test: $TEST_CLASS#$TEST_METHOD"
    else
        TEST_ARG="-Pandroid.testInstrumentationRunnerArguments.class=$TEST_CLASS"
        log_info "Running all tests in: $TEST_CLASS"
    fi
    
    # Clear logcat
    log_info "Clearing logcat..."
    adb logcat -c
    
    # Run tests with timeout
    log_info "Starting tests with 5 minute timeout..."
    log_info "Credentials: NORDVPN_USERNAME=${NORDVPN_USERNAME:0:3}***"
    
    if timeout 300 ./gradlew :app:connectedDebugAndroidTest \
        "$TEST_ARG" \
        -Pandroid.testInstrumentationRunnerArguments.NORDVPN_USERNAME="$NORDVPN_USERNAME" \
        -Pandroid.testInstrumentationRunnerArguments.NORDVPN_PASSWORD="$NORDVPN_PASSWORD"; then
        log_success "All tests passed!"
        return 0
    else
        log_error "Tests failed"
        log_info "Check logs with: adb logcat -d"
        return 1
    fi
}

# Show logs
show_logs() {
    if [ "${SHOW_LOGS:-false}" = "true" ]; then
        log_step "Recent Test Logs"
        log_info "Showing relevant log entries..."
        adb logcat -d | grep -E "(VpnEngineService|VpnConnectionManager|NativeOpenVpnClient|VpnRoutingTest|❌|✅|VPN)" | tail -50
    fi
}

# Main execution
main() {
    export QT_QPA_PLATFORM=${QT_QPA_PLATFORM:-xcb}
    echo -e "${GREEN}"
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║     Multi-Region VPN - E2E Test Runner                    ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    echo -e "${NC}\n"
    
    # Parse arguments
    TEST_CLASS=""
    TEST_METHOD=""
    SKIP_INSTALL=false
    SKIP_PERMISSIONS=false
    SKIP_DOCKER=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --test-class)
                TEST_CLASS="$2"
                shift 2
                ;;
            --test-method)
                TEST_METHOD="$2"
                shift 2
                ;;
            --skip-install)
                SKIP_INSTALL=true
                shift
                ;;
            --skip-permissions)
                SKIP_PERMISSIONS=true
                shift
                ;;
            --skip-docker)
                SKIP_DOCKER=true
                shift
                ;;
            --clear-data)
                CLEAR_APP_DATA=true
                shift
                ;;
            --show-logs)
                SHOW_LOGS=true
                shift
                ;;
            --help)
                cat << EOF
Usage: $0 [OPTIONS]

Options:
  --test-class CLASS       Run specific test class (default: VpnRoutingTest)
  --test-method METHOD     Run specific test method (requires --test-class)
  --skip-install           Skip app installation
  --skip-permissions       Skip permission granting
  --skip-docker            Skip Docker compose stack startup/shutdown
  --clear-data             Clear app data before running tests
  --show-logs              Show logs after tests complete
  --help                   Show this help message

Examples:
  $0                                    # Run all VpnRoutingTest tests
  $0 --test-method test_routesToUK      # Run single test method
  $0 --test-class VpnToggleTest        # Run different test class
  $0 --clear-data --show-logs          # Clear data and show logs

Environment Variables:
  CLEAR_APP_DATA=true                   Clear app data (same as --clear-data)
  SHOW_LOGS=true                        Show logs (same as --show-logs)
EOF
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                log_info "Use --help for usage information"
                exit 1
                ;;
        esac
    done
    
    # Set defaults
    TEST_CLASS="${1:-com.multiregionvpn.BasicConnectionTest}"
    
    # Run setup steps
    check_prerequisites
    setup_environment
    
    if [ "$SKIP_INSTALL" = false ]; then
        cleanup_installed_apps
        install_app
    else
        log_info "Skipping app installation (--skip-install)"
    fi
    
    if [ "$SKIP_PERMISSIONS" = false ]; then
        grant_permissions
    else
        log_info "Skipping permission granting (--skip-permissions)"
    fi
    
    start_docker_stack
    clear_app_data
    
    # Run tests
    if run_tests "$TEST_CLASS" "$TEST_METHOD"; then
        show_logs
        log_step "Test Execution Complete"
        log_success "All tests passed!"
        stop_docker_stack
        exit 0
    else
        show_logs
        log_step "Test Execution Complete"
        log_error "Some tests failed. Check logs for details."
        stop_docker_stack
        exit 1
    fi
}

# Run main function
main "$@"
