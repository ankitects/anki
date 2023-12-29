# Linux-specific notes

## Requirements

These instructions are written for Debian/Ubuntu; adjust for your distribution.
Some extra notes have been provided by a forum member, though some of the things
mentioned there no longer apply:
https://forums.ankiweb.net/t/guide-how-to-build-and-run-anki-from-source-with-xubuntu-20-04/12865

You can see a full list of buildtime and runtime requirements by looking at the
[Dockerfiles](../.buildkite/linux/docker/Dockerfile.amd64) used to build the
official releases.

Glibc is required - if you are on a distro like Alpine that uses musl, things
may not work.

Users on ARM64, see the notes at the bottom of this file before proceeding.

**Ensure some basic tools are installed**:

```
$ sudo apt install bash grep findutils curl gcc g++ make git rsync ninja-build
```

- The 'find' utility is 'findutils' on Debian.
- Your distro may call the package 'ninja' instead of 'ninja-build', or it
  may not have a version new enough - if so, install from the zip mentioned in
  development.md.

## Missing Libraries

If you get errors during build or startup, try starting with

QT_DEBUG_PLUGINS=1 ./run

It will likely complain about missing libraries, which you can install with
your package manager. Some of the libraries that might be required on Debian
for example:

```
sudo apt install libxcb-icccm4 libxcb-image0 libxcb-keysyms1 \
  libxcb-randr0 libxcb-render-util0
```

On some distros such as Arch Linux and Fedora, you may need to install the
`libxcrypt-compat` package if you get an error like this:

```
error while loading shared libraries: libcrypt.so.1: cannot open shared object file: No such file or directory
```

## Audio

To play and record audio during development, install mpv and lame.

## ARM64 support

Other platforms download PyQt binary wheels from PyPI. There are no PyQt wheels available
for ARM Linux, so you will need to rely on your system-provided libraries instead. Your distro
will need to have Python 3.9 or later.

After installing the system libraries (eg 'sudo apt install python3-pyqt5.qtwebengine python3-venv'),
find the place they are installed (eg '/usr/lib/python3/dist-packages'). On modern Ubuntu, you'll
need 'sudo apt remove python3-protobuf'. Then before running any commands like './run', tell Anki where
the packages can be found:

```
export PYTHONPATH=/usr/lib/python3/dist-packages
export PYTHON_BINARY=/usr/bin/python3
```

There are a few things to be aware of:

- You should use ./run and not tools/run-qt5\*, even if your system libraries are Qt5.
- If your system libraries are Qt5, when creating an aqt wheel, the wheel will not work
  on Qt6 environments.
- Some of the './ninja check' tests are broken on ARM Linux.

## Packaging considerations

Python, node and protoc are downloaded as part of the build. You can optionally define
PYTHON_BINARY, NODE_BINARY, YARN_BINARY and/or PROTOC_BINARY to use locally-installed versions instead.

If rust-toolchain.toml is removed, newer Rust versions can be used. Older versions
may or may not compile the code.

## More

For info on running tests, building wheels and so on, please see [Development](./development.md).
