# Windows

## Requirements

**Windows**:

You must be running 64 bit Windows 10, version 1703 or newer.

The build system requires [Developer Mode](https://docs.microsoft.com/en-us/windows/apps/get-started/enable-your-device-for-development) to be enabled.

**Visual Studio**:

Either the normal Visual Studio or just the [build tools](https://visualstudio.microsoft.com/downloads/#build-tools-for-visual-studio-2019) should work. Make sure the C++ compiler and Windows 10 SDK are selected - they should be on
by default.

**Python 3.8**:

Download Python 3.8 from <https://python.org>. Run the installer, and
customize the installation. Select "install for all users", and choose
the install path as c:\python. Currently the build scripts require
Python to be installed in that location.

**MSYS**:

Install [msys](https://www.msys2.org/) into the default folder location.

After installation completes, run msys, and run the following commands:

```
$ pacman -Syu
$ pacman -S git gettext
```

**Bazelisk**:

Use Start>Run to open PowerShell. Create a folder to store Bazelisk
and its working data. Anki's build scripts expect to find it in c:\bazel

```
PS> mkdir \bazel
PS> cd \bazel
```

Then grab Bazelisk:

```
PS> \msys64\usr\bin\curl -L https://github.com/bazelbuild/bazelisk/releases/download/v1.7.4/bazelisk-windows-amd64.exe -o bazel.exe
```

**Source folder**:

Anki's source files should not need to be in a specific location, but
the path should be kept as short as possible, and we test with the source
stored in c:\anki.

## Build failures

The Javascript bundling on Windows is currently a bit flaky, so the initial
build will likely fail with an error about a missing rollup module. If you
get an error when running the commands below, try repeating them once or twice.

## Running Anki during development

Open PowerShell and change to the top level of Anki's source folder,
then run

```
.\run
```

This will build Anki and run it in place.

The first build will take while, as it downloads and builds a bunch of
dependencies. When the build is complete, Anki will automatically start.

To play audio, mpv.exe or mplayer.exe must be on the path. At the time
of writing, recording is not yet supported, as currently pyaudio is
not being installed.

## More

For info on running tests, building wheels and so on, please see [Development](./development.md).

Note that where the instructions on that page say "bazel", please use ".\bazel"
instead. This runs bazel.bat inside the Anki source folder, instead of
calling Bazel directly. This takes care of setting up the path and output folder
correctly, which avoids issues with long path names.
