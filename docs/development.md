# Anki development

## Packaged betas

For non-developers who want to try beta versions, the easiest way is to use a
packaged version - please see:

https://betas.ankiweb.net/

## Pre-built Python wheels

Pre-built Python packages are available on PyPI. They are useful if you wish to:

- Run Anki from a local Python installation without building it yourself
- Get code completion when developing add-ons
- Make command line scripts that modify .anki2 files via Anki's Python libraries

You will need the 64 bit version of Python 3.9 or later installed. 3.9 is
recommended, as Anki has only received minimal testing on 3.10+ so far, and some
dependencies have not been fully updated yet. You can install Python from python.org
or from your distro.

For further instructions, please see https://betas.ankiweb.net/#via-pypipip. Note that
in the provided commands, `--pre` tells pip to fetch alpha/beta versions. If you remove
`--pre`, it will download the latest stable version instead.

## Building from source

On all platforms, you will need to install:

- Rustup (https://rustup.rs/). The Rust version pinned in rust-toolchain.toml
  will be automatically downloaded if not yet installed. If removing that file
  to use a distro-provided Rust, newer Rust versions will typically work for
  building but may fail tests; older Rust versions may not work at all.
- Ninja (unzip from https://github.com/ninja-build/ninja/releases/tag/v1.11.1 and
  place on your path, or from your distro/homebrew if it's 1.10+)

Platform-specific requirements:

- [Windows](./windows.md)
- [Mac](./mac.md)
- [Linux](./linux.md)

## Running Anki during development

From the top level of Anki's source folder:

```
./run
```

(`.\run` on Windows)

This will build Anki and run it in place.

The first build will take a while, as it downloads and builds a bunch of
dependencies. When the build is complete, Anki will automatically start.

## Running tests/checks

To run all tests at once, from the top-level folder:

```
./ninja check
```

(`tools\ninja check` on Windows).

You can also run specific checks. For example, if you see during the checks
that `check:svelte:editor` is failing, you can use `./ninja check:svelte:editor`
to re-run that check, or `./ninja check:svelte` to re-run all Svelte checks.

## Fixing formatting

When formatting issues are reported, they can be fixed with

```
./ninja format
```

## Fixing eslint/copyright header issues

```
./ninja fix
```

## Fixing clippy issues

```
cargo clippy --fix
```

## Optimized builds

The `./run` command will create a non-optimized build by default. This is faster
to compile, but will mean Anki will run slower.

To run Anki in optimized mode, use:

```
./tools/runopt
```

Or set RELEASE=1.

## Building redistributable wheels

The `./run` method described in the platform-specific instructions is a shortcut
for starting Anki directly from the build folder. For regular study, it's recommended
you build Python wheels and then install them into your own python venv. This is also
a good idea if you wish to install extra tools from PyPi that Anki's build process
does not use.

To build wheels on Mac/Linux:

```
./tools/build
```

(on Windows, `\tools\build.bat`)

The generated wheels are in out/wheels. You can then install them by copying the paths into a pip install command.
Follow the steps [on the beta site](https://betas.ankiweb.net/#via-pypipip), but replace the
`pip install --upgrade --pre aqt[qt6]` line with something like:

```
/my/pyenv/bin/pip install --upgrade out/wheels/*.whl
```

(On Windows you'll need to list out the filenames manually instead of using a wildcard).

You'll also need to install PyQt:

```
$ /my/pyenv/bin/pip install pyqt6 pyqt6-webengine
```

or

```
$ my/pyenv/bin/pip install pyqt5 pyqtwebengine
```

## Cleaning up build files

Apart from submodule checkouts, most build files go into the `out/` folder (and
`node_modules` on Windows). You can delete that folder for a clean build, or
to free space.

Cargo, yarn and pip all cache downloads of dependencies in a shared cache that
other builds on your system may use as well. If you wish to clear up those caches,
they can be found in `~/.rustup`, `~/.cargo` and `~/.cache/{yarn,pip}`.

If you invoke Rust outside of the build scripts (eg by running cargo, or
with Rust Analyzer), output files will go into `target/` unless you have
overriden the default output location.

## IDEs

Please see [this separate page](./editing.md) for setting up an editor/IDE.

## Making changes to the build

See [this page](./build.md)

## Environmental Variables

If ANKIDEV is set before starting Anki, some extra log messages will be printed on stdout,
and automatic backups will be disabled - so please don't use this except on a test profile.
It is automatically enabled when using ./run.

If TRACESQL is set, all SQL statements will be printed as they are executed.

If LOGTERM is set before starting Anki, warnings and error messages that are normally placed
in the collection2.log file will also be printed on stdout.

If ANKI_PROFILE_CODE is set, Python profiling data will be written on exit.

# Binary Bundles

Anki's official binary packages are created with `./ninja bundle`. The bundling
process was created specifically for the official builds, and is provided as-is;
we are unfortunately not able to provide assistance with any issues you may run
into when using it.

## Mixing development and study

You may wish to create a separate profile with File>Switch Profile for use
during development. You can pass the arguments "-p [profile name]" when starting
Anki to load a specific profile.

If you're using PyCharm:

- right click on the "run" file in the root of the PyCharm Anki folder
- click "Edit 'run'..." - in Script options and enter:
  "-p [dev profile name]" without the quotes
- click "Ok"
