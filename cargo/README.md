This folder integrates Rust crates.io fetching into Bazel.

After updating dependencies in ../rslib/Cargo.toml, change to this
folder and run python update.py to update the external Bazel repositories
to point to the updated deps.

You will need to have cargo-raze 0.7.0 or later installed, which is not
currently included in this Bazel project. You can install it by installing
rustup, then running "cargo install cargo-raze".

A couple of crates need extra work to build with Bazel, and are listed
in raze.toml. For example:

```toml
[raze.crates.ring.'*']
data_attr = "glob([\"src/**\"])"
```

With minor version updates, you should not normally need to modify
the entries in that file.

The ../pylib/rsbridge folder has a dependency on pyo3, which is
special-cased in update.py. If updating the pyo3 version, update.py
needs to be updated as well.
