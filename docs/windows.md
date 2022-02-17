# Windows

## Requirements

**Windows**:

You must be running 64 bit Windows 10, version 1703 or newer.

The build system requires [Developer Mode](https://docs.microsoft.com/en-us/windows/apps/get-started/enable-your-device-for-development) to be enabled.

**Visual Studio**:

Visual Studio 2022 was released in Nov 2021. **Anki does not support it yet**. You will
need to download 2019 instead. Microsoft will push you towards 2022 on the Visual Studio
website; you'll need to locate "older downloads", then log in with a free Microsoft
account and search for "Build Tools for Visual Studio 2019 (version 16.11)".

Once you've downloaded the installer, open it, and select "Desktop Development with C++"
on the left, leaving the options shown on the right as is.

**MSYS**:

Install [msys2](https://www.msys2.org/) into the default folder location.

After installation completes, run msys2, and run the following command:

```
$ pacman -S git
```

**Bazel**:

Use Start>Run to open PowerShell. Create a folder to store Bazel
and its working data. Anki's build scripts expect to find it in \bazel on the same drive as the source folder.

```
PS> mkdir \bazel
PS> cd \bazel
```

Then grab Bazelisk:

```
PS> \msys64\usr\bin\curl -L https://github.com/bazelbuild/bazelisk/releases/download/v1.10.1/bazelisk-windows-amd64.exe -o bazel.exe
```

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

To play and record audio, mpv.exe and lame.exe must be on the path.

## Optimized builds

The `.\run` command will create a non-optimized build by default. This is faster
to compile, but will mean Anki will run considerably slower.

To run Anki in optimized mode, use:

```
.\tools\runopt
```

## More

For info on running tests, building wheels and so on, please see
[Development](./development.md).

When you run a script like .\run, MSYS and bazel will automatically be added to
the path, and Bazel will be configured to output build products into
\bazel\anki. If you want to directly invoke bazel before having run any of the
.bat files in this repo, please run tools\setup-env first.
