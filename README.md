# [Anki](https://apps.ankiweb.net)

[![Build status](https://badge.buildkite.com/c9edf020a4aec976f9835e54751cc5409d843adbb66d043bd3.svg?branch=main)](https://buildkite.com/ankitects/anki-ci)

# About
[Anki](https://apps.ankiweb.net) is a program that helps you learn faster :fire: with user created flashcards.  
Our method makes learning easier and more efficient than traditional study methods.

Card content is endless as they support:
- images:framed_picture:
- audio:loud_sound:
- videos:film_strip:
- scientific markup (Via LaTeX):dna:

Just about anyone, studying just about anything, can benefit from [Anki](https://apps.ankiweb.net).

### Contributing to Anki :+1:
For more information on contributing to Anki, please see [Contributing](./contributing.md).

### Development Documentation :keyboard:
For more information on building, please see [Development](./docs/development.md).

This repo contains the source code for the computer version of
[Anki](https://apps.ankiweb.net). 

# Getting Started
## Packaged betas (for non-developers or trial builds)
- Please see [https://betas.ankiweb.net/](https://betas.ankiweb.net/) to try beta versions of Anki.

## Environment Setup: Python 🐍
### New to Python?
Check out [Python for Beginners](https://www.python.org/about/gettingstarted/)
### Python Packages: [PyPI](https://pypi.org/)
[PyPI](https://pypi.org/) provides pre-built Python packages for:

- runninng Anki locally without having to build it.
- getting code code completion when developing add-ons
- Making command line scripts that modify `.anki2` files via Anki's Python libraries

## Recommended Python Version(s)
### Python 3.9 or later (64-bit)
Python 3.9 is recommended
- Python 3.10+ has been minally tested and may not have the necessary dependencies.
**Get Python at [Python Downloads](https://www.python.org/downloads/)**

For more helping installing betas: https://betas.ankiweb.net/#via-pypipip. 
- Note that in the provided commands, `--pre` tells pip to fetch alpha/beta versions. If you remove
`--pre`, it will download the latest stable version instead.

# Building Anki from Source
New to Git, GitHub :octocat: or it's your first time contributing to a project? Get started [here](https://docs.github.com/en/get-started/quickstart/set-up-git).

## Fork and Clone the Repository
[Fork](https://docs.github.com/en/get-started/quickstart/fork-a-repo#forking-a-repository) the repository and [clone](https://docs.github.com/en/get-started/quickstart/fork-a-repo#cloning-your-forked-repository) the repository into the folder of your choosing.

## Pre-Build Installs
On all platforms, you will need to install:

### Rustup
- [Rustup](https://rustup.rs/) - a [Rust](https://www.rust-lang.org/) programming language installer
- You can find the rust version at [rust-toolchain.toml](./rust-toolchain.toml)
  will be automatically downloaded if not yet installed. If removing that file
  to use a distro-provided Rust, newer Rust versions will typically work for
  building but may fail tests; older Rust versions may not work at all.
### N2 or Ninja
- **N2** gives better status output. You can install it with:

```
tools/install-n2
```

or on Windows

```
bash tools\install-n2
```

- Ninja can be downloaded [here](https://github.com/ninja-build/ninja/releases/tag/v1.11.1) and placed on your path, or from your distro/homebrew if it's `1.10+`.

Platform-specific requirements:

- [Windows](./windows.md)
- [Mac](./mac.md)
- [Linux](./linux.md)

# Building and Runnning Anki
Enter the folder where you cloned the Anki repository and from the top level of your Anki source folder:
```
./run
```
or on Windows
```
.\run
```
Anki will build and run immediately after. 
- This first build can take awhile, so you may want to grab a coffee:coffee: or tea:teapot: while it downloads and builds dependencies.

# Development Documentation
Please see [Development](./docs/development.md) for:

- A more in depth Build explanation
  **or** 
- Running tests/checks
- Fixing formatting
- Fixing eslint/copyright header issues
- Fixing clippy issues
- Excluding your own untracked files from formatting and checks
- Optimized builds

# Additional Docs
- [Architecture](./architecture.md)
- [Build](./build.md)
- [Editing](./editing.md)

# License
Anki's licensing can be found in the [LICENSE](./LICENSE) file

### [Back to the Top](./README.md)