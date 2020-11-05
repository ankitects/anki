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

- Make sure your pip version is 20 or later (pip install --upgrade pip).
- Then:

```
$ python -m venv pyenv
$ pyenv/bin/pip install aqt anki pyqtwebengine
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

## Fixing formatting

If the format tests fail, most can be fixed by running format_fix
in the relevant folder:

```
bazel run //rslib:format_fix
bazel run //pylib:format_fix
bazel run //pylib/rsbridge:format_fix
bazel run //qt:format_fix
```

Currently the typescript code needs to be formatted differently:

```
cd ts
./node_modules/.bin/prettier --write .
```

## Building redistributable wheels

```
bazel build -c opt //pylib/anki:wheel
bazel build -c opt //qt/aqt:wheel
```

The generated wheel paths will be printed as the build completes. To install
them, see earlier in this document.

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

## Mixing development and study

You may wish to create a separate profile with File>Switch Profile for use
during development. You can pass the arguments "-p [profile name]" when starting
Anki to load a specific profile.

If you're using PyCharm:

- right click on the "run" file in the root of the PyCharm Anki folder
- click "Edit 'run'..." - in Script options and enter:
  "-p [dev profile name]" without the quotes
- click "Ok"

# Instructions need updating:

optional deps:
pyaudio
dpkg: portaudio19-dev

mpv
lame

1. Download and install the pyaudio wheel from: https://www.lfd.uci.edu/~gohlke/pythonlibs/#pyaudio
