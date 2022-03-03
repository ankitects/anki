# Editing/IDEs

Visual Studio Code is recommended, since it provides decent support for all the languages
Anki uses. If you open the root of this repo in VS Code, it will suggest some extensions
for you to install.

For editing Python, PyCharm/IntelliJ's type checking/completion is a bit nicer than
VS Code, but VS Code has improved considerably in a short span of time.

There are a few steps you'll want to take before you start using an IDE.

## Initial Setup

### Python Environment

For code completion of external Python modules, you'll need to create a Python
venv and install Anki's dependencies into it. For example:

```
$ python3.9 -m venv ~/pyenv
$ ~/pyenv/bin/pip install -r python/requirements.txt
$ ~/pyenv/bin/pip install pyqt6 pyqt6-webengine
```

After doing so, you can set your editor's Python path to ~/pyenv/bin/python, eg
in VS Code, ctrl/cmd+shift+p, then 'python: select interpreter'.

### Rust

If you're planning to edit Rust code, install [Rustup](https://rustup.rs/), then
run 'rustup install nightly'.

### Build First

Code completion partly depends on files that are generated as part of the
regular build process, so for things to work correctly, use './run' or
'tools/build' prior to using code completion.

## PyCharm/IntelliJ

If you decide to use PyCharm instead of VS Code, there are somethings to be aware of.

### Slowdowns

The build process links a large file tree into .bazel in the repo dir. JetBrains
products will try to monitor this folder for changes, and index the files inside
it, which will lead to bad performance. Excluding the folder in the project
settings is [not sufficient unfortunately](https://youtrack.jetbrains.com/issue/IDEA-73309).

A workaround is to add .bazel and node_modules to the IDE-global ignores:
https://intellij-support.jetbrains.com/hc/en-us/community/posts/115000721750-Excluding-directories-globally

### Pylib References

You'll need to use File>Project Structure to tell IntelliJ that pylib/ is a sources root, so it knows
references to 'anki' in aqt are valid.
