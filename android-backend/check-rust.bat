rustup component add clippy

@rem non-zero exit code on warnings
set RUSTFLAGS=-Dwarnings

cargo clippy
cd anki\cargo\format
cargo fmt --check --all --manifest-path ..\..\..\Cargo.toml
