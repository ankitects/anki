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

You will need the 64 bit version of Python 3.8 or 3.9 installed. If you do not
have Python yet, please see the platform-specific instructions in the "Building
from source" section below for more info.

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
- [Other Platforms](./new-platform.md)

Don't name the Git checkout ~/Anki or ~/Documents/Anki, as those folders
were used on old Anki versions and will be automatically moved.

Before contributing code, please see [Contributing](./contributing.md).

If you'd like to contribute translations, please see <https://translating.ankiweb.net/>.

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

You can run all tests at once. From the top level project folder:

```
bazel test ...
```

If you're in a subfolder, `...` will run the tests in that folder.
To run all tests, use `//...` instead.

Pylint will currently fail if you're using Python 3.9.

To run a single Rust unit test with output, eg 'unbury':

```
bazel run rslib:unit_tests -- --nocapture unbury
```

To run a single Python library test, eg test_bury:

```
PYTEST=test_bury bazel run //pylib:pytest
```

On Mac/Linux, after installing 'fswatch', you can run mypy on
each file save automatically with:

```
./scripts/mypy-watch
```

## Fixing formatting

If the format tests fail, most can be fixed by running `format`
in the relevant package:

```
bazel run //rslib:format
bazel run //rslib:sql_format
bazel run //proto:format
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

## Python editing

PyCharm or IntelliJ IDEA seems to give the best Python editing experience. Make sure
you build/run Anki first, as code completion depends on the build process to generate
a bunch of files.

After telling PyCharm to create a new virtual environment for your project, locate
pip in the virtual environment, and run `pip install -r pip/requirements.txt` to install
all of Anki's dependencies into the environment, so that code completion works for them.
Then run `pip install pyqt5 pyqtwebengine` to install PyQt.

Visual Studio Code + the Python extension does support code completion, but
currently seems to frequently freeze for multiple seconds while pinning the CPU
at 100%. Switching from the default Jedi language server to Pylance improves the
CPU usage, but Pylance doesn't do a great job understanding the type annotations.

## Rust editing

Currently Visual Studio Code + Rust Analyzer seems to be the best option out
there. Once Rust Analyzer is installed, you'll want to enable the options to
expand proc macros and build scripts, and run cargo check on startup. Adding
`+nightly` as an extra arg to rustfmt will get you nicer automatic formatting
of `use` statements.

The Bazel build products will make RA start up slowly out of the box. For a much
nicer experience, add each of the bazel-* folders to Rust Analyzer's excludeDirs
settings, and ts/node_modules. Wildcards don't work unfortunately. Then adjust
VS Code's "watcher exclude", and add `**/bazel-*`.

After running 'code' from the project root, it may take a minute or two to be
ready.
## TypeScript editing

Visual Studio Code seems to give the best experience. Use 'code ts' from the project
root to start it up.

IntelliJ IDEA works reasonably well, but doesn't seem to do as good a job at offering
useful completions for things like i18n.TR.

## Audio

Audio playing requires `mpv` or `mplayer` to be in your system path.

Recording also requires `lame` to be in your system path.

## Build errors and cleaning

If you get errors with @npm and node_modules in the message, try deleting the
ts/node_modules folder.

Unlike the old Make system, a "clean build" should almost never be required
unless you are debugging issues with the build system. But if you need to get
things to a fresh state, you can run `bazel clean --expunge`. Afte doing so,
make sure you remove the ts/node_modules folder, or subsequent build commands
will fail with a "no such file or directory node_modules/anki" message.

## Tracing build problems

You can run bazel with '-s' to print the commands that are being executed.

## Environmental Variables

If ANKIDEV is set before starting Anki, some extra log messages will be printed on stdout,
and automatic backups will be disabled - so please don't use this except on a test profile.

If TRACESQL is set, all sql statements will be printed as they are executed.

If LOGTERM is set before starting Anki, warnings and error messages that are normally placed
in the collection2.log file will also be printed on stdout.

If ANKI_PROFILE_CODE is set, Python profiling data will be written on exit.

## Mixing development and study

You may wish to create a separate profile with File>Switch Profile for use
during development. You can pass the arguments "-p [profile name]" when starting
Anki to load a specific profile.

If you're using PyCharm:

- right click on the "run" file in the root of the PyCharm Anki folder
- click "Edit 'run'..." - in Script options and enter:
  "-p [dev profile name]" without the quotes
- click "Ok"
