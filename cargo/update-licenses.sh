#!/bin/bash

cargo install cargo-license@0.5.1
cargo-license --features rustls --features native-tls --json --manifest-path ../rslib/Cargo.toml >licenses.json

cargo install cargo-deny@0.13.5
(cd .. && cargo deny check -A duplicate)
