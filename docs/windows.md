# Windows

## Requirements

**Windows**:

You must be running 64 bit Windows 10, version 1703 or newer.

The build system requires [Developer Mode](https://docs.microsoft.com/en-us/windows/apps/get-started/enable-your-device-for-development) to be enabled.

**Visual Studio**:

Install the [Visual Studio build tools](https://visualstudio.microsoft.com/downloads/#build-tools-for-visual-studio-2019).

Make sure the "C++ build tools" box is selected, and leave the default optional
components enabled on the right.

**Python 3.8**:

Download the 64 bit Python 3.8 from <https://python.org>. Run the installer,
and customize the installation. Select "install for all users", and choose
the install path as c:\python. Currently the build scripts require
Python to be installed in that location.

When the install is done, click on the "remove the path limit" button.

Python 3.9 is not currently recommended, as pylint does not support it yet.

**MSYS**:

Install [msys2](https://www.msys2.org/) into the default folder location.

After installation completes, run msys2, and run the following command:

```
$ pacman -S git
```

**Bazelisk**:

Use Start>Run to open PowerShell. Create a folder to store Bazelisk
and its working data. Anki's build scripts expect to find it in \bazel on the same drive as the source folder.

```
PS> mkdir \bazel
PS> cd \bazel
```

Then grab Bazelisk:

```
PS> \msys64\usr\bin\curl -L https://github.com/bazelbuild/bazelisk/releases/download/v1.7.4/bazelisk-windows-amd64.exe -o bazel.exe
```

NOTE: At the time of writing, Windows Defender is claiming this file has a virus. If it disappears
the first time you run Bazel, restoring it from the Defender settings should allow you to proceed.

**Source folder**:

Anki's source files do not need to be in a specific location other than on the
same drive as `\bazel`, but it's best to avoid long paths, as they can cause
problems.

## Running Anki during development

Open PowerShell and change to the top level of Anki's source folder,
then run

```
.\run
```

This will build Anki and run it in place.

The first build will take a while, as it downloads and builds a bunch of
dependencies. When the build is complete, Anki will automatically start.

The Javascript build code is currently a bit flaky, so the initial
build may fail with an error. If you get an error when running/building,
try repeating the command once or twice - it should pick up where it left off.

To play and record audio, mpv.exe and lame.exe must be on the path.

## Optimized builds

The `.\run` command will create a non-optimized build by default. This is faster
to compile, but will mean Anki will run considerably slower.

To run Anki in optimized mode, use:

```
.\scripts\runopt
```

## More

For info on running tests, building wheels and so on, please see
[Development](./development.md).

Note that where the instructions on that page say "bazel", please use ".\bazel"
instead. This runs bazel.bat inside the Anki source folder, instead of
calling Bazel directly. This takes care of setting up the path and output folder
correctly, which avoids issues with long path names.
