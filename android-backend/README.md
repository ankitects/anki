# AnkiDroid-Backend

An interface for accessing Anki Desktop's Rust backend inside AnkiDroid. This
allows AnkiDroid to re-use the computer version's business logic and webpages,
instead of having to reimplement them.

This is a separate repo that gets published to a library that AnkiDroid consumes,
so that AnkiDroid development is possible without a Rust toolchain installed.

## Prerequisites

We assume you already have Android Studio, and are able to build the AnkiDroid
project already.

The repos `Anki-Android` and `Anki-Android-Backend` should be cloned inside the
same folder. Furthermore, `Anki-Android-Backend` should not be renamed, as this
name is hard-coded in AnkiDroid gradle files. Unless stated otherwise, all
commands below are supposed to be executed in the current repo.

### Download Anki submodule

    git submodule update --init --recursive

### C toolchain

Install Xcode/Visual Studio if on macOS/Windows.

### Rust

Install rustup from <https://rustup.rs/>

#### macOS / Rust / Android Studio interaction

Note that Android Studio does a gradle sync when you open Anki-Android-Backend as a project,
and this sync requires the `cargo` command to be in your environment `PATH` variable to succeed.

If you get an exception related to `cargo` not found, you may need to alter your environment
before starting Android Studio. On macOS at least, you may open `Terminal`, verify `cargo` is
in the `PATH` with a `which cargo`, and open Android Studio directly with this environment setup
via the command `open -a "Android Studio"`

### Ninja

Anki can be built with Ninja or N2. N2 gives better status output and may be installed like so:

`./anki/tools/install-n2`

On Windows:

`bash anki/tools/install-n2`

*Note:* n2 receives occasional mandatory updates. If you see build errors, you may need to re-run this command and re-try the build

### NDK

#### Command-line install

Assuming `sdkmanager` from the "Command-Line Tools" package is in your PATH:

```bash
cargo install toml-cli
ANDROID_NDK_VERSION=$(toml get gradle/libs.versions.toml versions.ndk --raw)
sdkmanager --install "ndk;$ANDROID_NDK_VERSION"
```

#### GUI install

In Android Studio, choose the Tools>SDK Manager menu option.

- In SDK tools, enable "show package details"
- Choose NDK version listed in `gradle/libs.versions.tml` for the `ndk` key
- After downloading, you may need to restart Android Studio to get it to
synchronize gradle.

### Windows: msys2

Install [msys2](https://www.msys2.org/) into the default folder location.

After installation completes, run msys2, and run the following command:

```
pacman -S git rsync
```

When following the build steps below, make sure msys is on the path:

```
set PATH=%PATH%;c:\msys64\usr\bin
```

## Building

Two main files need to be built:

- The main .aar file, which contains the backend Kotlin code, web assets, and
Anki backend code compiled for Android.
- A .jar that contains the backend code compiled for the host platform, for use
with Robolectric unit tests.

You should do the first build with the provided shell .sh/.bat file, as it will
take care of downloading the target architecture library as well. You'll need
to tell the script to use the Java libraries and NDK downloaded by Android Studio,
eg on Linux:

```
cargo install toml-cli
ANDROID_NDK_VERSION=$(toml get gradle/libs.versions.toml versions.ndk --raw)
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/$ANDROID_NDK_VERSION
```

Or macOS:

```
cargo install toml-cli
ANDROID_NDK_VERSION=$(toml get gradle/libs.versions.toml versions.ndk --raw)
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/$ANDROID_NDK_VERSION
```

Or Windows using Powershell:

```
cargo install toml-cli
$env:ANDROID_NDK_VERSION=toml get gradle/libs.versions.toml versions.ndk --raw
$env:ANDROID_NDK_HOME="$env:ANDROID_HOME\ndk\$env:ANDROID_NDK_VERSION"
```

If you don't have Java installed, you may be able to use the version bundled
with Android Studio. Eg on macOS:

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jre/Contents/Home"
```

or Windows:

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jre
```

Now build with `./build.sh` or `build.bat`.

After you've confirmed building works, you may want to build again with the env
var RELEASE=1 defined, to build a faster version.

## Modify AnkiDroid to use built library

Now open the AnkiDroid project in AndroidStudio. To tell gradle to load the
compiled .aar and .jar files from disk, edit `local.properties`
in the AnkiDroid repo, and add the following line:

```
local_backend=true
```

Check `Anki-AndroidBackend/gradle.properties`'s `BACKEND_VERSION` and
`Anki-Android/build.gradle`'s `ext.ankidroid_backend_version`. Both variables
should have the same value. If it is not the case, you must edit Anki-Android's
one.

After making the change, you should be able to build and run the project on an x86_64
emulator/device (arm64 on M1 Macs), and run unit tests.

## Release builds

Only the current platform is built by default. In CI, the .aar and .jar files
are built for multiple platforms, so one release library can be used on a variety
of devices. See [the release workflow](https://github.com/ankidroid/Anki-Android-Backend/blob/main/.github/workflows/build-release.yml) for how this is done.

### Testing with a specific version of anki

In this section, we'll consider that you want to test AnkiDroid with the version
of Anki at commit `$COMMIT_IDENTIFIER` from the repository `some_repo`.

Most of the time `$SOME_REPO` will simply be `origin`, that is, ankitects
official repository, and `COMMIT_IDENTIFIER` could be replaced by the tag of the
latest stable release. You can find the latest tag by running `git tag|sort
-V|tail -n1` in the `anki` directory.

1. run `cd anki` to change into the anki submodule directory,
1. run `git fetch $SOME_REPO` to ensure you obtain the latest change from this repo.
1. run `git checkout $COMMIT_IDENTIFIER --recurse-submodules` to obtain the version of the code at this particular commit.
1. move back to the root of the repo (not the submodule) and run either `build.sh` or `build.bat` depending on your operating system,
then run `cargo check` to update our Cargo.lock with any updated versions from the submodule
1. make sure `rust-toolchain.toml` matches the rust version in the anki git submodule


### Creating and Publishing a release

Let's now consider that you want to release a new version of the back-end.

1. Find the latest stable version of Anki. You can find the latest tag by
running `git tag|sort -V|tail -n1` in the `anki` directory. Let's call it
version $ANKI_VERSION.
1. Ensure you are testing and building the back-end against this version (see
preceding section to learn how to do it).
1. In `Anki-Android-Backend/gradle.properties` you will need to update
`VERSION_NAME`. Its value is of the form
`$BACKEND_VERSION-$ANKI_VERSION`. `$ANKI_VERSION` should be as defined
above. `$BACKEND_VERSION` should be incremented compared to the last release.
1. Run the Github workflow `Build release (from macOS)` manually with a
string argument (I typically use `shipit`, but any string will work) - this will
trigger a full release build ready for upload to maven.
1. Check the workflow logs for the link to Maven Central where **if you have a
Maven Central user with permissions (like David A and Mike H - ask if you want
permission)** you may "close" the repository" then after a short wait "release"
the repository.
1. Head over to the main `Anki-Android` repository and update the
`AnkiDroid/build.gradle` file there to adopt the new backend version once it
shows up in
https://repo1.maven.org/maven2/io/github/david-allison/anki-android-backend/

## Architecture

See [ARCHITECTURE.md](./docs/ARCHITECTURE.md)

## License

[GPL-3.0 License](https://github.com/ankidroid/Anki-Android/blob/master/COPYING)  
[AGPL-3.0 Licence](https://github.com/AnkiDroid/anki/blob/main/LICENSE) (anki submodule)
