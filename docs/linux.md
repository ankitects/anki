# Linux

## Requirements

These instructions are written for Debian/Ubuntu; adjust for your distribution.

Glibc is required - if you are on a distro like Alpine that uses musl, you'll need
to contribute fixes to the upstream [Rust rules](https://github.com/bazelbuild/rules_rust/issues/390),
then follow the steps in [Other Platforms](./new-platform.md).

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

If you are using a packaged Python version that is installed in /usr/bin, you can jump
immediately to the next section.

If Python 3.8 is not available in your distro, you can download it from python.org,
compile it, and install it in /usr/local.

If you're on a basic Debian install, make sure you have the following installed
before building Python:

gcc g++ make libsqlite3-dev libreadline-dev libssl-dev zlib1g-dev libffi-dev

Bazel does not look in /usr/local by default. If you've installed Python somewhere
other than /usr/bin, you'll need to put the following into a file called user.bazelrc
at the top of this repo before proceeding:

```
build --action_env=PYTHON_SYS_EXECUTABLE=/usr/local/bin/python3.8
```

If you're building Anki from a docker container or distro that has no `python` command in
/usr/bin, you'll need to symlink `python` to `/usr/bin/python`. `/usr/bin/python` does not
need to be Python 3.8; any version will do.

If your system only has Python 3.9, you should be able to build Anki with it,
but the pylint tests will currently fail, as pylint does not yet support Python 3.9.

Anki's build system will not place packages in system locations, so you do not
need to build with an active Python virtual environment, and building outside
of one is recommended.

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

The first build will take a while, as it downloads and builds a bunch of
dependencies. When the build is complete, Anki will automatically start.

The Javascript build code is currently a bit flaky, so the initial
build may fail with an error. If you get an error when running/building,
try repeating the command once or twice - it should pick up where it left off.

To play and record audio, install mpv and lame.

If you or your distro has made ccache the standard compiler, you will need to
set CC and CXX to point directly to gcc/g++ or clang/clang++ prior to building
Anki.

## Optimized builds

The `./run` command will create a non-optimized build by default. This is faster
to compile, but will mean Anki will run considerably slower.

To run Anki in optimized mode, use:

```
./scripts/runopt
```

## More

For info on running tests, building wheels and so on, please see [Development](./development.md).
