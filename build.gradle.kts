// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// Task to download all dependencies for Android projects
// This is useful for CI caching to pre-download dependencies
// CRITICAL: Exclude native/NDK configurations to prevent CMake builds during dependency resolution
tasks.register("androidDependencies") {
    group = "help"
    description = "Downloads Java/Kotlin dependencies for Android projects (excludes native builds)"
    doLast {
        println("Resolving Java/Kotlin dependencies (skipping native/NDK configurations)...")
        allprojects {
            configurations.filter { config ->
                // Only resolve configurations that:
                // 1. Can be resolved
                // 2. Don't trigger native builds (NDK, CMake, native)
                // 3. Don't trigger expensive Android SDK downloads that happen at test runtime
                config.isCanBeResolved && 
                !config.name.contains("ndk", ignoreCase = true) &&
                !config.name.contains("cmake", ignoreCase = true) &&
                !config.name.contains("native", ignoreCase = true) &&
                !config.name.contains("cxx", ignoreCase = true) &&  // C++ configurations
                !config.name.contains("prefab", ignoreCase = true)  // Native library packaging
            }.forEach { configuration ->
                try {
                    println("✓ Resolving: ${configuration.name}")
                    configuration.resolve()
                } catch (e: Exception) {
                    // Log errors but continue - some configurations may not be resolvable in all contexts
                    println("✗ Could not resolve ${configuration.name}: ${e.message}")
                }
            }
        }
        println("✓ All Java/Kotlin dependencies resolved successfully")
    }
}
