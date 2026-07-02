## rsdroid-testing

### Why?

* `(lib)rsdroid` is a Rust library, targeted for Android
* Robolectric testing runs on native platforms (Windows/Mac/Linux etc...)
* This contains natively compiled code (.so/.dll/.dylib), which is loaded only for Robolectric Unit Tests

### How?

This project publishes a `jar` with precompiled native libraries. On execution, it extracts the correct library into a temp folder and loads it into the JVM at runtime.

### Relevant Code

* [Build `rsdroid` for platforms](https://github.com/ankidroid/Anki-Android-Backend/blob/main/rsdroid-testing/build.gradle): 
  * **Note**: using an old version of the rust toolchain to avoid cross-compilation bug for MacOS
* [Define folder of assets which is included in the `jar`](https://github.com/ankidroid/Anki-Android-Backend/blob/9302b3f89e09643e95c42166743f8563a3822ddc/rsdroid-testing/build.gradle#L31-L33) (permalink(
* [Publish Script](https://github.com/ankidroid/Anki-Android-Backend/blob/main/.github/workflows/publish_testing.yml)
* [Java method, a call extracts + loads assets into JVM](https://github.com/ankidroid/Anki-Android-Backend/blob/main/rsdroid-testing/src/main/java/net/ankiweb/rsdroid/testing/RustBackendLoader.java):

