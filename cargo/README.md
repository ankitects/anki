This folder integrates Rust crates.io fetching into Bazel.

After updating dependencies in ../rslib/Cargo.toml, change to this
folder and run python update.py to update the external Bazel repositories
to point to the updated deps.

You will need to have cargo-raze 0.7.0 or later installed, which is not
currently included in this Bazel project. When it's released as stable,
you can install it by installing rustup, then running "cargo install cargo-raze".
For now it needs to be built from commit 4d1721ed32e19dfea8794f868a4884bdffdc4014.

A couple of crates need extra work to build with Bazel, and are listed
in ../Cargo.toml. For example:

```toml
[package.metadata.raze.crates.pyo3.'*']
data_attr = "glob([\"**\"])"
```

With minor version updates, you should not normally need to modify
the entries in that file.
