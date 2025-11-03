# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep data classes
-keep class com.multiregionvpn.data.entity.** { *; }

# Keep Retrofit interfaces
-keepinterface class com.multiregionvpn.network.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# MockK rules - required for instrumentation tests
-keep class io.mockk.** { *; }
-keep class io.mockk.impl.** { *; }
-dontwarn io.mockk.**
