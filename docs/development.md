# Anki development

## Packaged betas

For non-developers who want to try this development code, the easiest way is
to use a packaged version - please see:

https://betas.ankiweb.net/#/

You are welcome to run Anki from source instead, but it is expected that you can
sort out issues by yourself - we are not able to provide support for problems
you encounter when running from source.

## Pre-built Python wheels

If you want to run Anki from a local Python installation but don't want
to make changes to the source code, you can install pre-built packages from PyPI.

For older versions:

```
$ python -m venv pyenv
$ pyenv/bin/pip install aqt anki ankirspy pyqt5 pyqtwebengine
$ pyenv/bin/python -c 'import aqt; aqt.run()'
```

From Anki 2.1.36 onwards:

```
$ python -m venv pyenv
$ pyenv/bin/pip install --upgrade pip
$ pyenv/bin/pip install aqt
$ pyenv/bin/python -c 'import aqt; aqt.run()'
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

## Building redistributable wheels

```
bazel build -c opt //pylib/anki:wheel
bazel build -c opt //qt/aqt:wheel
```

The generated wheel paths will be printed as the build completes. You can install
them with pip.

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
