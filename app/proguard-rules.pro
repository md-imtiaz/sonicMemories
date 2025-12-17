# Add project specific ProGuard rules here.

# Android Components
-keep class androidx.appcompat.widget.** { *; }
-keep class com.google.android.material.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class com.shuvostechworld.sonicmemories.data.remote.** { *; }
-keep class com.shuvostechworld.sonicmemories.data.model.** { *; }

# Hilt
-keep class com.shuvostechworld.sonicmemories.SonicMemoriesApp_HiltComponents { *; }
-keep class dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper$1
-keep class dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper$1 { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {
    <init>();
}

# Preserve Line Numbers for Crash Reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile