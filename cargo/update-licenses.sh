#!/bin/bash

cargo-license --features rustls native-tls --json --manifest-path ../rslib/Cargo.toml > licenses.json
