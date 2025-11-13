#!/bin/bash
echo "Installing Java 17..."
sudo pacman -S --noconfirm jdk17-openjdk
echo "Setting up Java 17..."
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH
echo "Java version:"
java -version
echo ""
echo "Now run: export JAVA_HOME=/usr/lib/jvm/java-17-openjdk && export PATH=\$JAVA_HOME/bin:\$PATH && ./gradlew installDebug"
