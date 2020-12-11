Anki's Rust code.

backend.proto stores the interfaces used to communicate backend messages between Rust, Python and TypeScript.

Rust editor support is still fairly new, but currently Visual Studio Code + Rust Analyzer seems to be the least bad option. For the latter, you'll want to enable the options to expand proc macros, and run cargo check on startup.

After running 'code .' from this folder, it may take Rust Analyzer a while to become ready, and you may need to save a file to trigger it to run.

You may also want to enable the worker mentioned in ../docs/development.md when compiling from a
terminal.
