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
        println("========================================")
        println("Resolving Java/Kotlin dependencies")
        println("Skipping native/NDK configurations")
        println("========================================")
        println("Timestamp: ${java.time.Instant.now()}")
        println("")
        
        var totalResolved = 0
        var totalFailed = 0
        
        allprojects {
            println("📦 Project: ${project.name}")
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
                    val startTime = System.currentTimeMillis()
                    println("  ✓ Resolving: ${configuration.name}")
                    val files = configuration.resolve()
                    val duration = System.currentTimeMillis() - startTime
                    println("    → Resolved ${files.size} files in ${duration}ms")
                    totalResolved++
                } catch (e: Exception) {
                    // Log errors but continue - some configurations may not be resolvable in all contexts
                    println("  ✗ Could not resolve ${configuration.name}")
                    println("    → Error: ${e.message}")
                    totalFailed++
                }
            }
            println("")
        }
        
        println("========================================")
        println("Summary:")
        println("  ✓ Successfully resolved: $totalResolved configurations")
        println("  ✗ Failed to resolve: $totalFailed configurations")
        println("Timestamp: ${java.time.Instant.now()}")
        println("========================================")
    }
}

// Optional task to pre-build native dependencies (for debugging native build issues)
tasks.register("buildNativeDependencies") {
    group = "help"
    description = "Pre-builds native libraries (OpenVPN, etc.) - use only for debugging"
    doLast {
        println("========================================")
        println("Building native dependencies")
        println("This will trigger CMake builds")
        println("========================================")
        // This task intentionally triggers native builds
        // Can be used separately to pre-cache native artifacts
    }
    // This will actually trigger the native build
    dependsOn("externalNativeBuildDebug")
}
