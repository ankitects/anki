# Mac

## Requirements

**Xcode**:

Install the latest XCode from the App Store. Open it at least once
so it installs the command line tools.

**Homebrew & Homebrew Deps**:

Install Homebrew from <https://brew.sh/>

Then install deps:

```
$ brew install rsync gettext bazelisk
```

**Install Python 3.8**:

Install Python 3.8 from <https://python.org>. You may be able to use
the Homebrew version instead, but this is untested.

You do not need to set up a Python venv prior to building Anki.

When you run "python" in a shell, if it shows Python 2.x, you may get a
bunch of hashlib warnings during build. You can work around this by
pointing python to python3.8:

```
$ ln -sf /usr/local/bin/{python3.8,python}
```

## Running Anki during development

From the top level of Anki's source folder:

```
./run
```

This will build Anki and run it in place.

The first build will take while, as it downloads and builds a bunch of
dependencies. When the build is complete, Anki will automatically start.

To play audio, use Homebrew to install mpv. At the time of writing, recording is
not yet supported, as currently pyaudio is not being installed.

## More

For info on running tests, building wheels and so on, please see [Development](./development.md).
