#!/bin/bash
# Script to apply patches to ics-openvpn build.gradle.kts to convert it to a library module

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ICSOpenVPN_DIR="$PROJECT_ROOT/libs/ics-openvpn"
MAIN_MODULE="$ICSOpenVPN_DIR/main"
BUILD_FILE="$MAIN_MODULE/build.gradle.kts"

if [ ! -f "$BUILD_FILE" ]; then
    echo "Error: build.gradle.kts not found at $BUILD_FILE"
    echo "Please run scripts/setup-ics-openvpn.sh first to clone the repository"
    exit 1
fi

echo "=========================================="
echo "Patching ics-openvpn build.gradle.kts"
echo "=========================================="
echo "Target: $BUILD_FILE"
echo ""

# Backup original if not already done
if [ ! -f "$BUILD_FILE.original" ]; then
    cp "$BUILD_FILE" "$BUILD_FILE.original"
    echo "Backed up original to build.gradle.kts.original"
fi

# Create a temporary patched file
PATCHED_FILE="$BUILD_FILE.patched"
cp "$BUILD_FILE.original" "$PATCHED_FILE"

# Apply patches using sed
echo "Applying patches..."

# 1. Change application to library
sed -i 's/id("com\.android\.application")/id("com.android.library")  \/\/ Changed from application to library/g' "$PATCHED_FILE"

# 2. Add namespace after android {
sed -i '/^android {/a\    namespace = "de.blinkt.openvpn"' "$PATCHED_FILE"

# 3. Update compileSdk (look for compileSdkVersion or compileSdk)
sed -i 's/compileSdkVersion(30)/compileSdk = 34  \/\/ Updated to match our project/g' "$PATCHED_FILE"
sed -i 's/compileSdk = 30/compileSdk = 34  \/\/ Updated to match our project/g' "$PATCHED_FILE"

# 4. Update minSdk (look for minSdkVersion or minSdk)
sed -i 's/minSdkVersion(14)/minSdk = 29  \/\/ Updated to match our project minimum/g' "$PATCHED_FILE"
sed -i 's/minSdk = 14/minSdk = 29  \/\/ Updated to match our project minimum/g' "$PATCHED_FILE"

# 5. Update targetSdk
sed -i 's/targetSdkVersion(30)/targetSdk = 34  \/\/ Updated to match our project/g' "$PATCHED_FILE"
sed -i 's/targetSdk = 30/targetSdk = 34  \/\/ Updated to match our project/g' "$PATCHED_FILE"

# 6. Remove versionCode and versionName (comment them out)
sed -i 's/^\(\s*\)versionCode = .*/\/\/ versionCode and versionName removed - not valid for library modules/g' "$PATCHED_FILE"
sed -i 's/^\(\s*\)versionName = .*/\/\/ (removed)/g' "$PATCHED_FILE"

# 7. Comment out externalNativeBuild (both instances)
sed -i 's/^\(\s*\)externalNativeBuild {/\/\/ Disable native build for now to simplify\n\1\/\/ externalNativeBuild {/g' "$PATCHED_FILE"
sed -i 's/^\(\s*\)cmake {/\/\/     cmake {/g' "$PATCHED_FILE"
sed -i 's/^\(\s*\)}\(.*cmake\)/\/\/     }/g' "$PATCHED_FILE"
sed -i 's/^\(\s*\)}\(.*externalNativeBuild\)/\/\/ }/g' "$PATCHED_FILE"

# 8. Remove flavor dimensions and product flavors (comment out)
sed -i 's/^\(\s*\)flavorDimensions(/\/\/ Flavor dimensions removed for simplicity\n\/\/ \1flavorDimensions(/g' "$PATCHED_FILE"
sed -i '/flavorDimensions/,/^}$/ {
    s/^\([^/]\)/\/\/ &/
}' "$PATCHED_FILE"
sed -i '/^\/\/\s*productFlavors {/,/^\/\/\s*}$/ {
    s/^\/\/\s*\([^/]\)/\/\/     \1/
}' "$PATCHED_FILE"

# 9. Remove flavor-specific sourceSets
sed -i 's/^\(\s*\)create("ui") {/\/\/ Removed flavor-specific source sets\n\/\/ \1create("ui") {/g' "$PATCHED_FILE"
sed -i 's/^\(\s*\)create("skeleton") {/\/\/ \1create("skeleton") {/g' "$PATCHED_FILE"

# 10. Remove signing configs
sed -i 's/^\(\s*\)signingConfigs {/\/\/ Signing configs removed - not needed for library module\n\/\/ \1signingConfigs {/g' "$PATCHED_FILE"
sed -i '/^\/\/\s*signingConfigs {/,/^}$/ {
    s/^\([^/]\)/\/\/ &/
}' "$PATCHED_FILE"

# 11. Remove ABI splits
sed -i 's/^\(\s*\)splits {/\/\/ ABI splits removed - not needed for library, handled by consuming app\n\/\/ \1splits {/g' "$PATCHED_FILE"
sed -i '/^\/\/\s*splits {/,/^}$/ {
    s/^\([^/]\)/\/\/ &/
}' "$PATCHED_FILE"

# 12. Simplify dependencies - comment out flavor-specific ones
sed -i 's/dependencies\.add("uiImplementation"/\/\/ Simplified dependencies - removed flavor-specific ones\n\/\/     dependencies.add("uiImplementation"/g' "$PATCHED_FILE"

# 13. Remove ApplicationVariant code at the end
sed -i '/android\.applicationVariants\.all/,/^}$/ {
    s/^/\/\/ /
}' "$PATCHED_FILE"

# 14. Remove checkstyle plugin (may not be needed)
sed -i 's/id("checkstyle")/\/\/ id("checkstyle")  \/\/ Removed for library module/g' "$PATCHED_FILE"

# Replace original with patched
mv "$PATCHED_FILE" "$BUILD_FILE"

echo ""
echo "=========================================="
echo "Patches applied successfully!"
echo "=========================================="
echo ""
echo "The build.gradle.kts has been converted to a library module."
echo "Original file backed up to: build.gradle.kts.original"
echo ""
echo "You may want to verify the changes manually."
echo ""


