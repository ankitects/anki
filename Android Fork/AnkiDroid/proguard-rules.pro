# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# #17256: `-dontobfuscate` caused crashes in SDK 26 (release mode):
# java.lang.NoSuchMethodError: No direct method <init>(II)V in class Lorg/apache/http/protocol/HttpRequestExecutor; or its super classes (declaration of 'org.apache.http.protocol.HttpRequestExecutor' appears in /system/framework/org.apache.http.legacy.boot.jar)
# The underlying cause has not been investigated, reinstate this line when fixed

# We do not have commercial interests to protect, so optimize for easier debugging
# -dontobfuscate

# Used through Reflection
# Prevent R8 static class merging from pulling in unrelated holders with primitive array fields.
# ACRA crashes handling these int[] arrays, leaving BuildConfig as 'N/A'
-keep class com.ichi2.anki.BuildConfig {
    public static <fields>;
}
-keep class com.ichi2.anki.**.*Fragment { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class androidx.core.app.ActivityCompat$* { *; }
-keep class androidx.concurrent.futures.** { *; }
-keep class androidx.appcompat.view.menu.MenuItemImpl { *; } # .utils.ext.MenuItemImpl

# used via exception.toString()
-keepnames class * extends java.lang.Throwable

# #23084: A Parcelable's class name is written into the Parcel. The system persists Parcels across app updates
# (ex. the extras of a PendingIntent held by AlarmManager), and R8 renames classes differently in each build,
# so a stored name could refer to a different, non-Parcelable class after an update
-keepnames class com.ichi2.** implements android.os.Parcelable

# Ignore unused packages
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**