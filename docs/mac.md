# Mac

## Requirements

**Xcode**:

Install the latest XCode from the App Store. Open it at least once
so it installs the command line tools.

**Homebrew & Homebrew Deps**:

Install Homebrew from <https://brew.sh/>

Then install deps:

```
$ brew install rsync bazelisk
```

**Install Python 3.8**:

Install Python 3.8 from <https://python.org>. We have heard reports
of issues with pyenv and homebrew, so the package from python.org is
the only recommended approach.

Python 3.9 is not currently recommended, as pylint does not support it yet.

You do not need to set up a Python venv prior to building Anki.

When you run "python" in a shell, if it shows Python 2.x, you may get a
bunch of hashlib warnings during build. You can work around this by
pointing python to python3.8:

```
$ ln -sf /usr/local/bin/{python3.8,python}
```

This linking will not work if you're using the system Python from Big Sur,
which is one of the reasons why we recommend using Python from python.org.

## Running Anki during development

From the top level of Anki's source folder:

```
./run
```

This will build Anki and run it in place.

The first build will take a while, as it downloads and builds a bunch of
dependencies. When the build is complete, Anki will automatically start.

The Javascript build code is currently a bit flaky, so the initial
build may fail with an error. If you get an error when running/building,
try repeating the command once or twice - it should pick up where it left off.

To play audio, use Homebrew to install mpv and lame.

## Optimized builds

The `./run` command will create a non-optimized build by default. This is faster
to compile, but will mean Anki will run considerably slower.

To run Anki in optimized mode, use:

```
./scripts/runopt
```

## More

For info on running tests, building wheels and so on, please see [Development](./development.md).
