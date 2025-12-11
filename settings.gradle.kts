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

// Diagnostic helper apps used by instrumentation tests
include(":diagnostic-client-uk", ":diagnostic-client-fr", ":diagnostic-client-direct")
project(":diagnostic-client-uk").projectDir = file("diagnostic-client-uk")
project(":diagnostic-client-fr").projectDir = file("diagnostic-client-fr")
project(":diagnostic-client-direct").projectDir = file("diagnostic-client-direct")

// VPN Protocol Support:
// - WireGuard: Primary protocol (via wireguard-android library)
// - OpenVPN: Future support (TBD - need to resolve TUN FD polling issue)
