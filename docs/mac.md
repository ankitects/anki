# Mac

## Requirements

**Xcode**:

Install the latest XCode from the App Store. Open it at least once
so it installs the command line tools.

**Bazelisk**:

```
$ curl -L https://github.com/bazelbuild/bazelisk/releases/download/v1.11.0/bazelisk-darwin -o bazel \
    && chmod +x bazel \
    && sudo mv bazel /usr/local/bin
```

**Python**:

The build system will automatically download a copy of Python 3.9 as part
of the build.

It is also possible to override the Python 3.9 that the build system uses.
We only recommend you do this if you have downloaded Python from python.org,
as we have heard reports of things failing when using a Python 3 from macOS
or Homebrew.

To override Python, put the following into a file called user.bazelrc at the top
of this repo (assuming /usr/local/bin/python links to your Python 3.9 binary).

```
build --action_env=PYO3_PYTHON=/usr/local/bin/python
```

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
./tools/runopt
```

## More

For info on running tests, building wheels and so on, please see [Development](./development.md).
