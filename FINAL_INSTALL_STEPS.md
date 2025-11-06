# Final Installation Steps

## Issue 1: Install unzip (required for Maestro)

```bash
sudo pacman -S unzip
```

Then install Maestro:
```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
export PATH="$HOME/.maestro/bin:$PATH"
maestro --version
```

## Issue 2: Java Version Compatibility

Your system has Java 25, but Gradle 8.2 is not fully compatible. You have two options:

### Option A: Use Java 21 (Recommended)

```bash
# Install Java 21 LTS
sudo pacman -S jdk21-openjdk

# Set JAVA_HOME (add to ~/.bashrc)
echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk' >> ~/.bashrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc

# Verify
java -version  # Should show Java 21
```

### Option B: Update Gradle (Alternative)

If you prefer to keep Java 25, we'd need to update Gradle to 8.10+ or 9.x. But this requires updating the Android Gradle Plugin and Kotlin versions too.

**Recommendation: Use Option A (Java 21)**

## After Fixing Java Version

Once you have Java 21 set up:

```bash
cd /home/pont/projects/multi-region-vpn

# Test the build
./gradlew --version

# Build the app (you'll need Android SDK first)
# ./gradlew build
```

## Complete Command Sequence

```bash
# 1. Install unzip
sudo pacman -S unzip

# 2. Install Java 21
sudo pacman -S jdk21-openjdk

# 3. Set Java 21 as default (add to ~/.bashrc)
echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk' >> ~/.bashrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc

# 4. Verify Java version
java -version

# 5. Install Maestro
curl -Ls "https://get.maestro.mobile.dev" | bash
export PATH="$HOME/.maestro/bin:$PATH"
maestro --version

# 6. Test Gradle wrapper
cd /home/pont/projects/multi-region-vpn
./gradlew --version

# 7. Connect device and build (once Android SDK is set up)
# adb devices
# ./gradlew installDebug
# maestro test .maestro/01_test_full_config_flow.yaml
```

## Note About Android SDK

To actually build the Android app, you'll also need:
- Android SDK (via Android Studio or command line tools)
- `ANDROID_HOME` environment variable set

But the Gradle wrapper and Maestro setup is now complete!


