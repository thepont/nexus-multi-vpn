plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val dotenv: Map<String, String> = run {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
                val idx = trimmed.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = trimmed.substring(0, idx).trim()
                var value = trimmed.substring(idx + 1).trim()
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length - 1)
                }
                key to value
            }
            .toMap()
    } else {
        emptyMap()
    }
}

fun envOrDotenv(key: String): String? =
    System.getenv(key)?.takeIf { it.isNotBlank() } ?: dotenv[key]?.takeIf { it.isNotBlank() }

android {
    namespace = "com.multiregionvpn"
    compileSdk = 34
    
    buildFeatures {
        buildConfig = true
    }
    
    // Android NDK version - will auto-detect if not specified
    // Uncomment and set if auto-detection fails:
    // ndkVersion = "26.1.10909125"
    
    defaultConfig {
        externalNativeBuild {
            cmake {
                // Build type will be set per build variant
                arguments += listOf(
                    "-DANDROID_STL=c++_shared"
                )
                // Enable vcpkg if VCPKG_ROOT environment variable is set
                // Usage: export VCPKG_ROOT=/path/to/vcpkg
                //        ./gradlew build
                // Note: For Android, vcpkg requires the Android triplet to be set
                // and dependencies must be installed for that triplet first
                // 
                // vcpkg chainloading: vcpkg's toolchain file will chainload Android's toolchain
                // CRITICAL: VCPKG_CHAINLOAD_TOOLCHAIN_FILE must be set BEFORE vcpkg.cmake is processed
                // So we set it as a command-line argument which CMake processes before toolchain files
                // Only enable vcpkg if VCPKG_ROOT is set AND the file exists
                val vcpkgRoot = System.getenv("VCPKG_ROOT")
                if (vcpkgRoot != null) {
                    val vcpkgToolchain = "$vcpkgRoot/scripts/buildsystems/vcpkg.cmake"
                    val vcpkgToolchainFile = file(vcpkgToolchain)
                    if (vcpkgToolchainFile.exists()) {
                            // Get Android NDK directory (not the toolchain file path yet)
                            // Use the actual NDK path from Gradle, which is more reliable
                            val androidNdkDir = "${android.ndkDirectory}"
                            
                            // Construct the full path to android.toolchain.cmake
                            // Android NDK has the toolchain file at: <ndk>/build/cmake/android.toolchain.cmake
                            val androidToolchainFile = "$androidNdkDir/build/cmake/android.toolchain.cmake"
                            
                            // Verify the file exists before using it
                            val androidToolchainFileObj = file(androidToolchainFile)
                            if (androidToolchainFileObj.exists()) {
                                // Don't set VCPKG_TARGET_TRIPLET here - let CMakeLists.txt auto-detect based on ANDROID_ABI
                                arguments += listOf(
                                    "-DUSE_VCPKG=ON",
                                    "-DVCPKG_CHAINLOAD_TOOLCHAIN_FILE=$androidToolchainFile"
                                )
                            } else {
                                // Fallback: try to find it in common locations
                                val fallbackPaths = listOf(
                                    "${System.getenv("ANDROID_NDK")}/build/cmake/android.toolchain.cmake",
                                    "${System.getenv("ANDROID_NDK_HOME")}/build/cmake/android.toolchain.cmake",
                                    "${System.getenv("ANDROID_HOME")}/ndk-bundle/build/cmake/android.toolchain.cmake"
                                )
                                val foundPath = fallbackPaths.firstOrNull { file(it).exists() }
                                if (foundPath != null) {
                                    arguments += listOf(
                                        "-DUSE_VCPKG=ON",
                                        "-DVCPKG_CHAINLOAD_TOOLCHAIN_FILE=$foundPath"
                                    )
                                } else {
                                    // If we can't find it, skip vcpkg for this build
                                    println("Warning: Could not find android.toolchain.cmake, skipping vcpkg")
                                }
                            }
                        
                        // Set vcpkg toolchain file - this will be processed by CMake
                        // and vcpkg.cmake will include the chainloaded Android toolchain
                        arguments += listOf("-DCMAKE_TOOLCHAIN_FILE=$vcpkgToolchain")
                    }
                }
                cppFlags += listOf(
                    "-std=c++20",  // Required for OpenVPN 3
                    "-fexceptions",
                    "-frtti"
                )
            }
        }
        
        ndk {
            // Build for both ARM architectures - vcpkg libraries built for both triplets
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    
    // Disable native builds in CI to prevent timeouts
    // Native builds can take 15-30 minutes and aren't needed for unit tests
    val isCI = System.getenv("CI")?.toBoolean() ?: false
    if (!isCI) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    } else {
        println("⚠️  CI environment detected - skipping native build configuration")
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.multiregionvpn"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        testInstrumentationRunner = "com.multiregionvpn.HiltTestRunner"
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
        envOrDotenv("NORDVPN_USERNAME")?.let { username ->
            testInstrumentationRunnerArguments["NORDVPN_USERNAME"] = username
        }
        envOrDotenv("NORDVPN_PASSWORD")?.let { password ->
            testInstrumentationRunnerArguments["NORDVPN_PASSWORD"] = password
        }
        envOrDotenv("BASELINE_IP_OVERRIDE")?.let { ip ->
            testInstrumentationRunnerArguments["BASELINE_IP_OVERRIDE"] = ip
        }
        envOrDotenv("BASELINE_COUNTRY_OVERRIDE")?.let { country ->
            testInstrumentationRunnerArguments["BASELINE_COUNTRY_OVERRIDE"] = country
        }
        // Allow passing test arguments for E2E tests (e.g., NordVPN credentials)
        // Usage: -Pandroid.testInstrumentationRunnerArguments.NORDVPN_USERNAME='user' -Pandroid.testInstrumentationRunnerArguments.NORDVPN_PASSWORD='pass'
        multiDexEnabled = true
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    testOptions {
        animationsDisabled = true
        unitTests {
            isReturnDefaultValues = true
        }
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/LICENSE-notice.md"
            excludes += "/META-INF/**"
        }
        // Ensure MockK classes are included in test APK
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildTypes {
        debug {
            // Debug builds don't require vcpkg - use pre-built libraries or skip native build
            // This allows faster iteration during development
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
            )
        }
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Android TV Compose - D-pad optimized components
    implementation("androidx.tv:tv-material:1.0.0-alpha10")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.leanback:leanback:1.1.0-rc02")
    
    // Room
    val roomVersion = "2.6.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // VPN Protocol Support
    // WireGuard: Primary protocol (fast, modern, excellent multi-tunnel support)
    implementation("com.wireguard.android:tunnel:1.0.20230706")
    
    // OpenVPN: Kept in codebase for future use (currently disabled due to TUN FD polling issue)
    // Native OpenVPN 3 code is in app/src/main/cpp/ but not actively used
    // Can be re-enabled once TUN FD polling is fixed or alternative library is integrated
    
    // BouncyCastle for cryptographic operations (used by OpenVPN)
    // Note: ics-openvpn may already include BouncyCastle, but we'll keep these for compatibility
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // LocalBroadcastManager (for error broadcasting - deprecated but still functional)
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.robolectric:robolectric:4.12.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    
    // UI Automator for E2E Testing
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestUtil(files("${rootDir}/diagnostic-client-uk/build/outputs/apk/debug/diagnostic-client-uk-debug.apk"))
    androidTestUtil(files("${rootDir}/diagnostic-client-fr/build/outputs/apk/debug/diagnostic-client-fr-debug.apk"))
    androidTestUtil(files("${rootDir}/diagnostic-client-direct/build/outputs/apk/debug/diagnostic-client-direct-debug.apk"))
    
    // Mocking - Using Mockito for Android tests (more reliable than MockK on Android)
    testImplementation("io.mockk:mockk:1.13.10")
    androidTestImplementation("org.mockito:mockito-android:5.11.0")
    androidTestImplementation("org.mockito:mockito-core:5.11.0")
    androidTestImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    
    // Keep MockK for unit tests (it works fine there)
    androidTestImplementation("io.mockk:mockk-android:1.13.10") // Try keeping it too
    
    // Coroutines Testing (ESSENTIAL for ViewModels)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    
    // Room (for DAO testing)
    androidTestImplementation("androidx.room:room-testing:2.6.0")
    
    // Jetpack (for ViewModel testing)
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    
    // Truth (for more fluent assertions, like BDD)
    testImplementation("com.google.truth:truth:1.1.3")
    androidTestImplementation("com.google.truth:truth:1.1.3")
    
    // E2E Test Dependencies
    androidTestImplementation("com.squareup.retrofit2:retrofit:2.9.0")
    androidTestImplementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    androidTestImplementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1") // Hilt compiler for tests
    androidTestImplementation("androidx.test:rules:1.5.0")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

gradle.projectsEvaluated {
    tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
        dependsOn(
            ":diagnostic-client-uk:assembleDebug",
            ":diagnostic-client-fr:assembleDebug",
            ":diagnostic-client-direct:assembleDebug"
        )
    }
}
