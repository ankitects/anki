# Linux

## Requirements

These instructions are written for Debian/Ubuntu; adjust for your distribution.

**Ensure some basic tools are installed**:

```
$ sudo apt install bash grep findutils curl gcc g++ git
```

The 'find' utility is 'findutils' on Debian.

**Install Python 3.8**:

If you're on a modern distribution, you may be able to install Python from the repo:

```
$  sudo apt install python3.8
```

If python3.8 is not available in your distro, you can download it from python.org.

Notes:

- The build scripts expect to find 'python3.8' on your path, so Python 3.7 or 3.9 will
  not work.
- An active Python venv is not required, and may cause problems.

**Install Bazelisk**:

Download it under the name 'bazel':

```
$ curl -L https://github.com/bazelbuild/bazelisk/releases/download/v1.7.4/bazelisk-linux-amd64 -o ./bazel
```

And put it on your path:

```
$ chmod +x bazel && sudo mv bazel /usr/local/bin/
```

## Running Anki during development

From the top level of Anki's source folder:

```
./run
```

This will build Anki and run it in place.

The first build will take while, as it downloads and builds a bunch of
dependencies. When the build is complete, Anki will automatically start.

To play audio, install mpv. At the time of writing, recording is
not yet supported, as currently pyaudio is not being installed.

## More

For info on running tests, building wheels and so on, please see [Development](./development.md).
