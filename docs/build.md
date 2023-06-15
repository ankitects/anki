# The build system

## Basic use

Basic use is described in [development.md](./development.md).

## Architecture

The build/ folder is made up of 4 packages:

- build/configure defines the actions and inputs/outputs of the build graph -
  this is where you add new build steps or modify existing ones. The defined
  actions are converted at build time to a build.ninja file that Ninja executes.
- build/ninja_gen is a library for writing a build.ninja file, and includes
  various rules like "build a Rust crate" or "run a command".
- build/archives is a helper to download/checksum/extract a dependency as part
  of the build process.
- build/runner serves a number of purposes:
  - it's the entrypoint to the build process, taking care of generating
    the build file and then invoking Ninja
  - it wraps executable invocations in the build file, swallowing their output
    if they exit successfully
  - it provides a few helpers for multi-step processes that can't be easily
    described in a cross-platform manner thanks to differences on Windows.

## Tracing build problems

If you run into trouble with the build process:

- You can see the executed commands with e.g. `./ninja pylib -v`
- You can see the output of successful commands by defining OUTPUT_SUCCESS=1
- You can see what's triggering a rebuild of a target with e.g.
  `./ninja qt/anki -d explain`.
- You can browse the build graph via e.g. `./ninja -- -t browse wheels`
- You can profile build performance with
  https://discourse.cmake.org/t/profiling-build-performance/2443/3.

## Packaging considerations

See [this page](./linux.md).
