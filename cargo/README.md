This folder integrates Rust crates.io fetching into Bazel.

To update dependencies, ensure a local Rust environment is available
(eg install rustup), then run:

```
cargo install cargo-raze --version 0.8.0
```

After updating dependencies in ../rslib/Cargo.toml, change to this
folder and run python update.py to update the external Bazel repositories
to point to the updated deps.

A couple of crates need extra work to build with Bazel, and are listed
in ../Cargo.toml. For example:

```toml
[package.metadata.raze.crates.pyo3.'*']
compile_data_attr = "glob([\"**\"])"
```

With minor version updates, you should not normally need to modify
the entries in that file.

Because update.py modifies a lot of files in remote/, it makes it difficult to
review in a PR, and the changes can sometimes break platforms like Windows. For
this reason, please don't submit PRs that do minor version bumps - those will
typically be done after stable releases. If you need a new crate for a feature
you're working on, please raise it in an issue first.
