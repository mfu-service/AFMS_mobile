# AFMS Production Proguard Rules

# 1. WebView - Keep JavaScript interfaces
-keepattributes JavascriptInterface
-keep class dz.afms.mobile.AfmsBridge { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 2. Support for Layout Direction and Orientation changes
-keep class androidx.compose.ui.platform.AndroidComposeView { *; }

# 3. Optimization settings for R8
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
