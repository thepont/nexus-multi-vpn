# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Optimize aggressively
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-dontpreverify
-verbose

# Keep data classes and database entities
-keep class com.multiregionvpn.data.entity.** { *; }
-keep class com.multiregionvpn.data.database.** { *; }

# Keep Retrofit interfaces
-keepinterface class com.multiregionvpn.network.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep VPN client classes (needed for reflection and runtime instantiation)
-keep class com.multiregionvpn.core.vpnclient.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Room entities and DAOs
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Keep Compose runtime classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Remove logging in release builds (saves space)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# MockK rules - required for instrumentation tests
-keep class io.mockk.** { *; }
-keep class io.mockk.impl.** { *; }
-dontwarn io.mockk.**

# WireGuard - keep what's needed
-keep class com.wireguard.** { *; }
-dontwarn com.wireguard.**

# BouncyCastle - keep what's needed
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.ViewModel$* { *; }
