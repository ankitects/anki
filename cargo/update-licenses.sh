#!/bin/bash

cargo-license --features rustls --features native-tls --json --manifest-path ../rslib/Cargo.toml >licenses.json
