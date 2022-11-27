# Windows

## Requirements

**Windows**:

You must be running 64 bit Windows 10, version 1703 or newer.

The build system previously required [Developer Mode](https://docs.microsoft.com/en-us/windows/apps/get-started/enable-your-device-for-development) to be enabled. This may no longer
be the case.

**Visual Studio**:

Install Visual Studio from Microsoft. Once you've downloaded the installer, open
it, and select "Desktop Development with C++" on the left, leaving the options
shown on the right as is.

**MSYS**:

Install [msys2](https://www.msys2.org/) into the default folder location.

After installation completes, run msys2, and run the following command:

```
$ pacman -S git rsync
```

If you have native Windows apps relying on Git, e.g. the PowerShell extension
[posh-git](https://github.com/dahlbyk/posh-git), you may want to install
[Git for Windows](https://gitforwindows.org/) and put it on the path instead,
as msys Git may cause issues with them. You'll need to make sure rsync is
available some other way.

**Source folder**:

Anki's source files do not need to be in a specific location, but it's best to
avoid long paths, as they can cause problems. Spaces in the path may cause
problems.

## Audio

To play and record audio during development, mpv.exe and lame.exe must be on the path.

## More

For info on running tests, building wheels and so on, please see
[Development](./development.md).
