This folder integrates Rust crates.io fetching into Bazel.

After updating dependencies in ../rslib/Cargo.toml, change to this
folder and run python update.py to update the external Bazel repositories
to point to the updated deps.

You will need to have cargo-raze 0.6.1 or later installed, which is not
currently included in this Bazel project. You can install it by installing
rustup, then running "cargo install cargo-raze".

A couple of crates need extra work to build with Bazel, and are listed
in raze.toml. For example:

```toml
[raze.crates.ring.'0.16.15']
data_attr = "glob([\"src/**\"])"
```

After updating dependencies, if one of these crates has changed to a
new version, the version in raze.toml will need to be updated, and
update.py re-run.

The ../pylib/rsbridge folder has a dependency on pyo3, which is
special-cases in update.py. If updating the pyo3 version, raze.toml
and update.py need to be updated as well.
