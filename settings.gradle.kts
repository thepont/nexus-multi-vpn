pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // For ics-openvpn if available
    }
}

rootProject.name = "MultiRegionVPN"
include(":app")

// OpenVPN 3 is integrated via native C++ (JNI), not as a Gradle module
// Removed ics-openvpn dependency - using OpenVPN 3 C++ library instead
