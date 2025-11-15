# Release Workflow Documentation

## Overview

The release workflow automatically builds and publishes a universal Android APK when a new version tag is pushed to the repository.

## Features

- **Automatic Trigger**: Workflow runs when you push a git tag matching `v*` pattern (e.g., `v1.0.0`, `v2.1.3`)
- **Manual Trigger**: Can also be triggered manually via GitHub Actions UI
- **Universal APK**: Builds a single APK containing all supported ABIs (arm64-v8a, armeabi-v7a)
- **GitHub Release**: Automatically creates a GitHub Release with the APK attached
- **Build Artifacts**: Uploads build logs for debugging

## Usage

### Triggering a Release

#### Option 1: Via Git Tag (Recommended)

```bash
# Create and push a version tag
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

The workflow will automatically:
1. Build the release APK
2. Create a GitHub Release
3. Upload the APK as a release artifact

#### Option 2: Manual Trigger

1. Go to the GitHub repository
2. Click on "Actions" tab
3. Select "Release" workflow
4. Click "Run workflow"
5. Enter the tag name (e.g., `v1.0.0`)
6. Click "Run workflow"

### Downloading the Release

After the workflow completes:

1. Go to the repository's "Releases" page
2. Find your release (e.g., "Release v1.0.0")
3. Download the APK file from the Assets section
4. Install on your Android device

## APK Details

The generated APK includes:
- **Supported ABIs**: arm64-v8a (64-bit ARM), armeabi-v7a (32-bit ARM)
- **Min SDK**: Android 10 (API 29)
- **Target SDK**: Android 14 (API 34)
- **Native Libraries**: OpenVPN 3, WireGuard support

## Code Signing

### Current Status

⚠️ **The current workflow builds an unsigned APK.** This is suitable for:
- Development testing
- Internal distribution
- Personal use

### For Production Distribution

For production releases (Google Play Store, wide distribution), you need to add code signing.

#### Adding Code Signing to the Workflow

1. **Generate a Signing Key** (if you don't have one):

```bash
keytool -genkey -v -keystore my-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias my-key-alias
```

2. **Encode the Keystore** for GitHub Secrets:

```bash
base64 my-release-key.jks > keystore.base64
```

3. **Add GitHub Secrets**:

Go to Repository Settings → Secrets and Variables → Actions → New repository secret

Add these secrets:
- `RELEASE_KEYSTORE`: Content of `keystore.base64`
- `RELEASE_KEYSTORE_PASSWORD`: Your keystore password
- `RELEASE_KEY_ALIAS`: Your key alias (e.g., `my-key-alias`)
- `RELEASE_KEY_PASSWORD`: Your key password

4. **Update `app/build.gradle.kts`**:

```kotlin
android {
    signingConfigs {
        create("release") {
            // Load from environment variables (set by CI)
            storeFile = file(System.getenv("RELEASE_KEYSTORE_FILE") ?: "dummy.jks")
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

5. **Update the Workflow** (`.github/workflows/release.yml`):

Add this step before "Build Universal Release APK":

```yaml
      - name: Decode Keystore
        env:
          RELEASE_KEYSTORE: ${{ secrets.RELEASE_KEYSTORE }}
        run: |
          echo "$RELEASE_KEYSTORE" | base64 -d > app/release-keystore.jks

      - name: Build Signed Release APK
        env:
          CI: false
          RELEASE_KEYSTORE_FILE: ${{ github.workspace }}/app/release-keystore.jks
          RELEASE_KEYSTORE_PASSWORD: ${{ secrets.RELEASE_KEYSTORE_PASSWORD }}
          RELEASE_KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
          RELEASE_KEY_PASSWORD: ${{ secrets.RELEASE_KEY_PASSWORD }}
        run: |
          # ... existing build command
```

## Troubleshooting

### Build Fails

- Check the "Actions" tab for detailed logs
- Download the `release-build-logs` artifact for full build output
- Ensure all dependencies are properly configured

### APK Installation Fails

- Enable "Install from unknown sources" on your Android device
- For signed APKs, ensure the keystore is valid
- Check that the APK is compatible with your device's architecture

### Workflow Doesn't Trigger

- Ensure the tag matches the pattern `v*` (e.g., `v1.0.0`, not `1.0.0`)
- Verify you pushed the tag: `git push origin v1.0.0`
- Check repository permissions for Actions

## Version Numbering

Recommended semantic versioning format:
- `v1.0.0` - Major release
- `v1.1.0` - Minor release (new features)
- `v1.0.1` - Patch release (bug fixes)
- `v1.0.0-beta.1` - Pre-release versions

## Build Time

Expected build time: 20-40 minutes
- Native library compilation (OpenVPN C++)
- Multi-ABI build (arm64-v8a + armeabi-v7a)
- Dependency resolution

## Next Steps

1. Test the workflow by creating a test release tag
2. Consider adding code signing for production
3. Optionally add ProGuard/R8 for code optimization
4. Consider App Bundle (AAB) format for Google Play Store
