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
    TEST_CLASS="${1:-com.multiregionvpn.NordVpnE2ETest}"
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
    log_info "Starting tests with 2 minute timeout..."
    log_info "Credentials: NORDVPN_USERNAME=${NORDVPN_USERNAME:0:3}***"
    
    if timeout 120 ./gradlew :app:connectedDebugAndroidTest \
        "$TEST_ARG"; then
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
        adb logcat -d | grep -E "(VpnEngineService|VpnConnectionManager|NativeOpenVpnClient|NordVpnE2ETest|❌|✅|VPN)" | tail -50
    fi
}

# Main execution
main() {
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
  --test-class CLASS       Run specific test class (default: NordVpnE2ETest)
  --test-method METHOD     Run specific test method (requires --test-class)
  --skip-install           Skip app installation
  --skip-permissions       Skip permission granting
  --clear-data             Clear app data before running tests
  --show-logs              Show logs after tests complete
  --help                   Show this help message

Examples:
  $0                                    # Run all NordVpnE2ETest tests
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
    TEST_CLASS="${TEST_CLASS:-com.multiregionvpn.NordVpnE2ETest}"
    
    # Run setup steps
    check_prerequisites
    setup_environment
    
    if [ "$SKIP_INSTALL" = false ]; then
        install_app
    else
        log_info "Skipping app installation (--skip-install)"
    fi
    
    if [ "$SKIP_PERMISSIONS" = false ]; then
        grant_permissions
    else
        log_info "Skipping permission granting (--skip-permissions)"
    fi
    
    clear_app_data
    
    # Run tests
    if run_tests "$TEST_CLASS" "$TEST_METHOD"; then
        show_logs
        log_step "Test Execution Complete"
        log_success "All tests passed!"
        exit 0
    else
        show_logs
        log_step "Test Execution Complete"
        log_error "Some tests failed. Check logs for details."
        exit 1
    fi
}

# Run main function
main "$@"
