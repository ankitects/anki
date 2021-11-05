This folder integrates Rust crates.io fetching into Bazel.

To update dependencies, ensure a local Rust environment is available
(eg `source scripts/cargo-env`), then install cargo-raze:

```
cargo install cargo-raze --version 0.13.0
cargo install cargo-license
```

After updating dependencies in ../rslib/Cargo.toml, change to this
folder and run python update.py to update the external Bazel repositories
to point to the updated deps.

Note: cargo-raze does not currently work when run from Windows, and nobody
has investigated why yet. For now, you'll need a Mac or Linux machine, or
will need to run update.py from within WSL.

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

## Reqwest

Things are complicated with reqwest at the moment, because:

-   we're using a fork to implement better timeouts for syncing
-   we want to build it with different features on Linux (where we can't build a
    wheel that links to OpenSSL), and on other platforms.

For minor version bumps, update.py should take care of updating the versions of
reqwest dependencies.

After making a big update to reqwest via an updated fork, the vendored
BUILD.reqwest.\* files may need updating. To do that, comment native-tls from
the features in rslib/Cargo.toml and run update.py, and copy the file in remote/
over the old vendored file. Then comment the other two deps out, add native-tls
back, and repeat the process.
