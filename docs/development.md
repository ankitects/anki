# Anki development

## Packaged betas

For non-developers who want to try beta versions, the easiest way is to use a
packaged version - please see:

https://betas.ankiweb.net/

## Pre-built Python wheels

Pre-built Python packages are available on PyPI. They are useful if you wish to:

-   Run Anki from a local Python installation without building it yourself
-   Get code completion when developing add-ons
-   Make command line scripts that modify .anki2 files via Anki's Python libraries

You will need the 64 bit version of Python 3.9 or 3.10 installed. 3.9 is recommended,
as Anki has only received minimal testing on 3.10 so far, and some dependencies have not
been fully updated yet. On Windows, only 3.9 will work. You can install Python from
python.org or from your distro.

For further instructions, please see https://betas.ankiweb.net/#via-pypipip. Note that
in the provided commands, `--pre` tells pip to fetch alpha/beta versions. If you remove
`--pre`, it will download the latest stable version instead.

## Building from source

Platform-specific instructions:

-   [Windows](./windows.md)
-   [Mac](./mac.md)
-   [Linux](./linux.md)
-   [Other Platforms](./new-platform.md)

Before contributing code, please see [Contributing](./contributing.md).

If you'd like to contribute translations, please see <https://translating.ankiweb.net/>.

## Building redistributable wheels

The `./run` method described in the platform-specific instructions is a shortcut
for starting Anki directly from Bazel. This is useful for quickly running Anki
after making source code changes, but requires Bazel to be available, and will
not play nicely with the debugging facilities in IDEs. For daily Anki, or using
third-party Python tools, you'll want to build Python wheels instead.

The Python wheels are standard Python packages that can be installed with pip.
You'll typically want to install them into a a dedicated Python virtual environment (venv),
so that the dependencies are kept isolated from those of other packages on your system.
While you can 'pip install' them directly using the system Python, other packages on your
system may depend on different versions of those dependencies, which can cause breakages.

Run the following command to create Python packages:

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
Follow the steps [on the beta site](https://betas.ankiweb.net/#via-pypipip), but replace the
`pip install --upgrade --pre aqt[qt6]` line with something like:

```
pyenv/bin/pip install --upgrade bazel-dist/*.whl
```

(On Windows you'll need to list out the filenames manually instead of using a wildcard).

You'll also need to install PyQt:

```
$ pyenv/bin/pip install pyqt6 pyqt6-webengine
```

or

```
$ pyenv/bin/pip install pyqt5 pyqtwebengine
```

## Freeing Space

The build process will download about a gigabyte of dependencies, and produce
about 6 gigabytes of temporary files. Once you've created the wheels, you can
remove the other files to free up space if you wish.

-   `bazel clean --expunge` will remove the generated files, freeing up most
    of the space. The files are usualy stored in a subdir of ~/.cache/bazel/
-   `rm -rf ~/.cache/bazel*` will remove the cached downloads as well, requiring
    them to be redownloaded if you want to build again.
-   `rm -rf ~/.cache/{yarn,pip}` will remove the shared pip and yarn caches that
    other apps may be using as well.

## Building with Docker

Linux users can build using the instructions above, or they can optionally [build
via Docker](../scripts/docker/README.md).

On Linux, the generated Anki wheel will have a filename like:

    anki-2.1.49-cp39-abi3-manylinux_2_31_aarch64.whl

The 2_31 part means that the wheel requires glibc 2.31 or later. If you have
built the wheel on a machine with an older glibc version, you will get an error
if you try to install the wheel:

    ERROR: No matching distribution found for anki

To avoid the error, you can rename the .whl file to match your glibc version.

If you still get the error, another possibility is that you are trying to
install with an old version of Python - 3.9 or later is required.

On ARM Linux, please see the instructions in the pre-built wheels section about
a system PyQt, and the notes at the bottom of [Linux](./linux.md).

## Running tests

You can run all tests at once. From the top level project folder:

```
bazel test ...
```

If you're in a subfolder, `...` will run the tests in that folder.
To run all tests, use `//...` instead.

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

For formatting issues with .ts, .svelte and .md files, change to the folder
that's causing the problem, and then run

```
bazel run //ts:format
```

For other packages, change to the folder and run

```
bazel run format
```

For the latter cases, you can also invoke the formatter from another folder by using
the full path:

```
bazel run //rslib:format
bazel run //rslib:sql_format
bazel run //proto:format
bazel run //pylib:format
bazel run //qt:format
bazel run //pylib/rsbridge:format
```

## Development speedups

If you're frequently switching between Anki versions, you can create
a user.bazelrc file in the top level folder with the following, which will
cache build products:

```
build --disk_cache=~/.cache/bazel/disk
```

It will grow with each changed build, and needs to be manually removed
when you wish to free up space.

## Python editing

PyCharm or IntelliJ IDEA seems to give the best Python editing experience. Make sure
you build/run Anki first, as code completion depends on the build process to generate
a bunch of files.

After telling PyCharm to create a new virtual environment for your project, locate
pip in the virtual environment, and run `pip install -r pip/requirements.txt` to install
all of Anki's dependencies into the environment, so that code completion works for them.
Then run `pip install pyqt6 pyqt6-webengine` to install PyQt.

Visual Studio Code + the Python extension does support code completion, but Pylance
doesn't do a great job understanding the type annotations.

## Rust editing

Currently Visual Studio Code + Rust Analyzer seems to be the best option out
there. Once Rust Analyzer is installed, you'll want to enable the options to
expand proc macros and build scripts, and run cargo check on startup. Adding
`+nightly` as an extra arg to rustfmt will get you nicer automatic formatting
of `use` statements.

The Bazel build products will make RA start up slowly out of the box. For a much
nicer experience, add each of the `bazel-*` folders to Rust Analyzer's excludeDirs
settings, and node_modules. Wildcards don't work unfortunately. Then adjust
VS Code's "watcher exclude", and add `\*\*/bazel-*`.

After running `code .` from the project root, it may take a minute or two to be
ready.

## TypeScript editing

Visual Studio Code seems to give the best experience. Use `code ts` or `code .`
from the project root to start it up.

IntelliJ IDEA works reasonably well, but doesn't seem to do as good a job at offering
useful completions for things like translation strings.

## Audio

Audio playing requires `mpv` or `mplayer` to be in your system path.

Recording also requires `lame` to be in your system path.

## Build errors and cleaning

If you get errors with @npm and node_modules in the message, try deleting the
node_modules folder.

On Windows, you may run into 'could not write file' messages when TypeScript
files are renamed, as the old build products are not being cleaned up correctly.
You can either remove the problem folder (eg
.bazel/out/x64_windows-fastbuild/bin/ts/projectname), or do a full clean.

To do a full clean, use a `bazel clean --expunge`, and then remove the node_modules
folder.

## Tracing build problems

You can run bazel with '-s' to print the commands that are being executed.

## Environmental Variables

If ANKIDEV is set before starting Anki, some extra log messages will be printed on stdout,
and automatic backups will be disabled - so please don't use this except on a test profile.

If TRACESQL is set, all SQL statements will be printed as they are executed.

If LOGTERM is set before starting Anki, warnings and error messages that are normally placed
in the collection2.log file will also be printed on stdout.

If ANKI_PROFILE_CODE is set, Python profiling data will be written on exit.

## Mixing development and study

You may wish to create a separate profile with File>Switch Profile for use
during development. You can pass the arguments "-p [profile name]" when starting
Anki to load a specific profile.

If you're using PyCharm:

-   right click on the "run" file in the root of the PyCharm Anki folder
-   click "Edit 'run'..." - in Script options and enter:
    "-p [dev profile name]" without the quotes
-   click "Ok"
