#!/bin/bash

set -e

(cd anki/cargo/format && cargo fmt --all --manifest-path ../../../Cargo.toml)
