This folder integrates Rust crates.io fetching into Bazel.

After updating dependencies in ../rslib/Cargo.toml, change to this
folder and run python update.py to update the external Bazel repositories
to point to the updated deps.

Currently you'll need to:

- have a local Rust environment installed
- fetch cargo-raze from GitHub
- check out 52f20dda88da0feb696ec2fea32e82840d203c13
- then change to the impl folder, and run 'cargo install --path .' to install it.

A couple of crates need extra work to build with Bazel, and are listed
in ../Cargo.toml. For example:

```toml
[package.metadata.raze.crates.pyo3.'*']
compile_data_attr = "glob([\"**\"])"
```

With minor version updates, you should not normally need to modify
the entries in that file.
