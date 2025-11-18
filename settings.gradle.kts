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
include(":diagnostic-client-uk")
include(":diagnostic-client-fr")
include(":diagnostic-client-direct")

// VPN Protocol Support:
// - WireGuard: Primary protocol (via wireguard-android library)
// - OpenVPN: Future support (TBD - need to resolve TUN FD polling issue)
