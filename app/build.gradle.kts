plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

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
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release"
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
                if (System.getenv("VCPKG_ROOT") != null) {
                    val vcpkgRoot = System.getenv("VCPKG_ROOT")
                    
                    // Get Android NDK directory (not the toolchain file path yet)
                    val androidNdkDir = System.getenv("ANDROID_NDK") ?: 
                                       System.getenv("ANDROID_NDK_HOME") ?: 
                                       "${android.ndkDirectory}"
                    
                    // Construct the full path to android.toolchain.cmake
                    val androidToolchainFile = "$androidNdkDir/build/cmake/android.toolchain.cmake"
                    
                    // CRITICAL: Set chainload file FIRST, before CMAKE_TOOLCHAIN_FILE
                    // This ensures vcpkg.cmake can find it when it processes the chainload
                    // The path must be the full path to android.toolchain.cmake, not just the NDK directory
                    arguments += listOf(
                        "-DUSE_VCPKG=ON",
                        "-DVCPKG_TARGET_TRIPLET=arm64-android",
                        "-DVCPKG_CHAINLOAD_TOOLCHAIN_FILE=$androidToolchainFile"
                    )
                    
                    // Set vcpkg toolchain file - this will be processed by CMake
                    // and vcpkg.cmake will include the chainloaded Android toolchain
                    val vcpkgToolchain = "$vcpkgRoot/scripts/buildsystems/vcpkg.cmake"
                    arguments += listOf("-DCMAKE_TOOLCHAIN_FILE=$vcpkgToolchain")
                }
                cppFlags += listOf(
                    "-std=c++20",  // Required for OpenVPN 3
                    "-fexceptions",
                    "-frtti"
                )
            }
        }
        
        ndk {
            // For now, only build arm64-v8a since vcpkg libraries are built for arm64-android
            // TODO: Build vcpkg libraries for other ABIs (armeabi-v7a, x86, x86_64) when needed
            abiFilters += listOf("arm64-v8a")
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
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
    
    // OpenVPN Implementation - Using OpenVPN 3 C++ via JNI
    // Native library is built via CMake (see src/main/cpp/CMakeLists.txt)
    // The old ics-openvpn dependency has been replaced with native implementation
    // implementation(project(":openvpn")) // REMOVED - using native OpenVPN 3 instead
    
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
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    
    // UI Automator for E2E Testing
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    
    // Mocking - Using Mockito for Android tests (more reliable than MockK on Android)
    testImplementation("io.mockk:mockk:1.13.10")
    androidTestImplementation("org.mockito:mockito-android:5.11.0")
    androidTestImplementation("org.mockito:mockito-core:5.11.0")
    androidTestImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    
    // Keep MockK for unit tests (it works fine there)
    androidTestImplementation("io.mockk:mockk-android:1.13.10") // Try keeping it too
    
    // Coroutines Testing (ESSENTIAL for ViewModels)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    
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
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1") // Hilt compiler for tests
    androidTestImplementation("androidx.test:rules:1.5.0")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
