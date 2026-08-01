#!/bin/bash

cargo install cargo-criterion --version 1.1.0
cargo criterion --bench benchmark --features bench
