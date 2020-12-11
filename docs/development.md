# Anki development

## Packaged betas

For non-developers who want to try beta versions, the easiest way is to use a
packaged version - please see:

https://betas.ankiweb.net/#/

## Pre-built Python wheels

Pre-built Python packages are available on PyPI. They are useful if you wish to:

- Run Anki from a local Python installation without building it yourself
- Get code completion when developing add-ons
- Make command line scripts that modify .anki2 files via Anki's Python libraries

You will need Python 3.8 or 3.9 installed. If you do not have Python yet, please
see the platform-specific instructions in the "Building from source" section below
for more info.

**Mac/Linux**:

```
$ python3.8 -m venv ~/pyenv
$ ~/pyenv/bin/pip install --upgrade pip
$ ~/pyenv/bin/pip install aqt
```

Then to run Anki:

```
$ ~/pyenv/bin/anki
```

**Windows**:

```
c:\> python -m venv \pyenv
c:\> \pyenv\scripts\pip install --upgrade pip
c:\> \pyenv\scripts\pip install aqt
```

Then to run Anki:

```
c:\> \pyenv\scripts\anki
```

## Building from source

Platform-specific instructions:

- [Windows](./windows.md)
- [Mac](./mac.md)
- [Linux](./linux.md)

Don't name the Git checkout ~/Anki or ~/Documents/Anki, as those folders
were used on old Anki versions and will be automatically moved.

Before contributing code, please see [Contributing](./contributing.md).

If you'd like to contribute translations, please see <https://translating.ankiweb.net/#/>.

## Building redistributable wheels

Run the following command to create Python packages that can be redistributed
and installed:

On Mac/Linux:

```
./scripts/build
```

On Windows:

```
.\scripts\build.bat
```

The generated wheel paths will be printed as the build completes.

You can then install them by copying the paths into a pip install command.
Follow the steps in the "Pre-built Python wheels" section above, but replace the
"pip install aqt" line with something like:

```
pip install --upgrade bazel-dist/*.whl
```

On Windows you'll need to list out the filenames manually.

## Running tests

From inside the source folder:

```
bazel test //...
```

Pylint will currently fail if you're using Python 3.9.

## Fixing formatting

If the format tests fail, most can be fixed by running `format`
in the relevant package:

```
bazel run //rslib:format
bazel run //pylib:format
bazel run //qt:format
bazel run //ts:format
bazel run //pylib/rsbridge:format
```

If you're in one of those folders, you can use the short form:

```
bazel run format
```

## Development speedups

If you're frequently switching between Anki versions, you can create
a user.bazelrc file in the top level folder with the following, which will
cache build products and downloads:

```
build --disk_cache=~/bazel/ankidisk --repository_cache=~/bazel/ankirepo
```

If you're frequently modifying the Rust parts of Anki, you can place the
following in your user.bazelrc file to enable incremental compilation:

```
build --@io_bazel_rules_rust//worker:use_worker=True
build:windows --worker_quit_after_build
```

The worker support is experimental, so you may need to remove it in future
updates.

## Audio

Audio playing requires `mpv` or `mplayer` to be in your system path.

Currently pyaudio is not included as part of the build or the generated wheel
requirements, so audio recording will not work when running in place. When installing
the wheels, you can optionally install pyaudio as well.

On Linux/Mac, install the portaudio libs: (`apt install portaudio19-dev` / `brew install portaudio`), then `pip install pyaudio`.

On Windows, install the Python 3.8 wheel from
https://github.com/ankitects/windows-ci-tools.

Recording also requires `lame` to be in your system path.

## Tracing build problems

You can run bazel with '-s' to print the commands that are being executed.

## Subcomponents

- pylib contains a Python module (anki) with the non-GUI Python code,
  and a bridge to the Rust code.
- qt contains the Qt GUI implementation (aqt).
- rslib contains the parts of the code implemented in Rust.
- ts contains Anki's typescript and sass files.

## Environmental Variables

If ANKIDEV is set before starting Anki, some extra log messages will be printed on stdout,
and automatic backups will be disabled - so please don't use this except on a test profile.

If LOGTERM is set before starting Anki, warnings and error messages that are normally placed
in the collection2.log file will also be printed on stdout.

## Cleaning

Unlike the old Make system, a "clean build" should almost never be required
unless you are debugging issues with the build system. But if you need to get
things to a fresh state, you can run `bazel clean --expunge`. Afte doing so,
make sure you remove the ts/node_modules folder, or subsequent build commands
will fail with a "no such file or directory node_modules/anki" message.

## Mixing development and study

You may wish to create a separate profile with File>Switch Profile for use
during development. You can pass the arguments "-p [profile name]" when starting
Anki to load a specific profile.

If you're using PyCharm:

- right click on the "run" file in the root of the PyCharm Anki folder
- click "Edit 'run'..." - in Script options and enter:
  "-p [dev profile name]" without the quotes
- click "Ok"
