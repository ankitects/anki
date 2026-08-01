# These proguard rules are only needed when building
# for the combination of testing and release mode
# Certain androidx frameworks that are test-only have
# issues with proguard / minimization in release mode

# Used for testing
-keep class kotlin.test.** { *; }
-keep class **.R$layout { <init> (...); <fields>; }

# Used by some test classes, not important for us
-dontwarn androidx.concurrent.futures.SuspendToFutureAdapter

# Ignore unused packages
-dontwarn com.google.protobuf.GeneratedMessageLite$*