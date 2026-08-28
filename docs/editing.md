# Editing/IDEs

Visual Studio Code is recommended, since it provides decent support for all the languages
Anki uses. To set up the recommended workspace settings for VS Code, please see below.

For editing Python, PyCharm/IntelliJ's type checking/completion is a bit nicer than
VS Code, but VS Code has improved considerably in a short span of time.

There are a few steps you'll want to take before you start using an IDE.

## Initial Setup

### Python Environment

For code completion of external Python modules, you can use the venv that is
generated as part of the build process. After building Anki, the venv will be in
`out/pyenv`. In VS Code, use ctrl/cmd+shift+p, then 'python: select
interpreter'.

### Rust

You'll need Rust to be installed, which is required as part of the build process.

### Build First

Code completion partly depends on files that are generated as part of the
regular build process, so for things to work correctly, use './run' or
'tools/build' prior to using code completion.

## Visual Studio Code

### Setting up Recommended Workspace Settings

To start off with some default workspace settings that are optimized for Anki
development, please head to the project root and then run:

```
mkdir .vscode && cd .vscode
ln -sf ../.vscode.dist/* .
```

### Installing Recommended Extensions

Once the workspace settings are set up, open the root of the repo in VS Code to
see and install a number of recommended extensions.

## PyCharm/IntelliJ

### Setting up Python environment

To make PyCharm recognize `anki` and `aqt` imports, you need to add source paths to _Settings > Project Structure_.
You can copy the provided .idea.dist directory to set up the paths automatically:

```
mkdir .idea && cd .idea
ln -sf ../.idea.dist/* .
```

You also need to add a new Python interpreter under _Settings > Python > Interpreter_ pointing to the Python executable under `out/pyenv` (available after building Anki).
