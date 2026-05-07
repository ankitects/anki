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
-keep class com.ichi2.anki.**.*Fragment { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class androidx.core.app.ActivityCompat$* { *; }
-keep class androidx.concurrent.futures.** { *; }
-keep class androidx.appcompat.view.menu.MenuItemImpl { *; } # .utils.ext.MenuItemImpl
-keep class com.ichi2.anki.settings.PrefsRepository { *; } # PrefsRepository.notificationsPermissionRequested

# used via exception.toString()
-keepnames class * extends java.lang.Throwable

# Ignore unused packages
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**