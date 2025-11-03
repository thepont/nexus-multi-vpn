# Fix Java Build Issue

## Problem
Android Gradle Plugin 8.2.0 has compatibility issues with Java 21's `jlink` tool.

## Solution
Install Java 17 (LTS) which is fully compatible with AGP 8.2.0.

## Quick Fix

Run these commands:

```bash
# Install Java 17
sudo pacman -S --noconfirm jdk17-openjdk

# Set Java 17 as active
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH

# Verify Java version (should show 17.x)
java -version

# Clean and build
cd /home/pont/projects/multi-region-vpn
rm -rf ~/.gradle/caches/transforms-3/*
./gradlew clean installDebug
```

## Permanent Setup (Optional)

Add to `~/.bashrc`:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH
```

The `gradle.properties` file already points to Java 17, so once installed, builds should work automatically.

