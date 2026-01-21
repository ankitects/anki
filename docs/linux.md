# Linux-specific notes

## Requirements

These instructions are written for Debian/Ubuntu; adjust for your distribution.
Some extra notes have been provided by a forum member, though some of the things
mentioned there no longer apply:
https://forums.ankiweb.net/t/guide-how-to-build-and-run-anki-from-source-with-xubuntu-20-04/12865

You can see a full list of buildtime and runtime requirements by looking at the
[Dockerfile](../.buildkite/linux/docker/Dockerfile) used to build the
official releases.

**Ensure some basic tools are installed**:

```
$ sudo apt install bash grep findutils curl gcc gcc-12 g++ make git rsync
```

- The 'find' utility is 'findutils' on Debian.

## Missing Libraries

If you get errors during build or startup, try starting with

QT_DEBUG_PLUGINS=1 ./run

It will likely complain about missing libraries, which you can install with
your package manager. Some of the libraries that might be required on Debian
for example:

```
sudo apt install libxcb-icccm4 libxcb-image0 libxcb-keysyms1 \
  libxcb-randr0 libxcb-render-util0 libxkbfile1
```

The libraries that might be required on Arch Linux:

```
sudo pacman -S nss libxkbfile
```

On some distros such as Fedora, you may need to install the
`libxcrypt-compat` package if you get an error like this:

```
error while loading shared libraries: libcrypt.so.1: cannot open shared object file: No such file or directory
```

## Audio

To play and record audio during development, install mpv and lame.

## Glibc and Qt

Anki requires a recent glibc.

If you are using a distro that uses musl, Anki will not work.

You can use your system's Qt libraries if they are Qt 6.2 or later, if
you wish. After installing the system libraries (eg:
'sudo apt install python3-pyqt6.qt{quick,webengine} python3-venv pyqt6-dev-tools'),
find the place they are installed (eg '/usr/lib/python3/dist-packages'). On modern Ubuntu, you'll
also need 'sudo apt remove python3-protobuf'. Then before running any commands like './run', tell Anki where
the packages can be found:

```
export PYTHONPATH=/usr/lib/python3/dist-packages
export PYTHON_BINARY=/usr/bin/python3
```

## Packaging considerations

Python, node and protoc are downloaded as part of the build. You can optionally define
PYTHON_BINARY, NODE_BINARY, YARN_BINARY and/or PROTOC_BINARY to use locally-installed versions instead.

If rust-toolchain.toml is removed, newer Rust versions can be used. Older versions
may or may not compile the code.

To build Anki fully offline, set the following environment variables:

- OFFLINE_BUILD: If set, the build does not run tools that may access
  the network.

- NODE_BINARY, YARN_BINARY and PROTOC_BINARY must also be set.

With OFFLINE_BUILD defined, manual intervention is required for the
offline build to succeed. The following conditions must be met:

1. All required dependencies (node, Python, rust, yarn, etc.) must be
   present in the build environment.

2. The offline repositories for the translation files must be
   copied/linked to ftl/qt-repo and ftl/core-repo.

3. The Python pseudo venv must be set up:

   ```
   mkdir out/pyenv/bin
   ln -s /path/to/python out/pyenv/bin/python
   ln -s /path/to/protoc-gen-mypy out/pyenv/bin/protoc-gen-mypy
   ```

   Optionally, set up your environment to generate Sphinx documentation:

   ```
   ln -s /path/to/sphinx-apidoc out/pyenv/bin/sphinx-apidoc
   ln -s /path/to/sphinx-build out/pyenv/bin/sphinx-build
   ```

   Note that the PYTHON_BINARY environment variable need not be set,
   since it is only used when OFFLINE_BUILD is unset to automatically
   create a network-dependent Python venv.

4. Create the offline cache for yarn and use its own environment
   variable YARN_CACHE_FOLDER to it:

   ```
   YARN_CACHE_FOLDER=/path/to/the/yarn/cache
   /path/to/yarn install --ignore-scripts
   ```

You are now ready to build wheels and Sphinx documentation fully
offline.

## More

For info on running tests, building wheels and so on, please see [Development](./development.md).
