# Rust API

Anki's Rust API documentation is generated from doc comments in the `rslib/` crates using `cargo doc`.
It is distributed as crates on [crates.io](https://crates.io/), and the public documentation (when published) is generated automatically on [docs.rs](https://docs.rs/).

## Browsing locally

To generate and open the docs locally:

```sh
cargo doc --open
```

## Main crates

- **anki** (`rslib/`) — core library
  - crates.io: https://crates.io/crates/anki
  - docs.rs: https://docs.rs/anki/

## Supporting crates (published as dependencies)

The `anki` crate depends on additional workspace crates.

Because of that, these supporting crates need to be published as well (typically before publishing `anki`).

- **anki_io** (`rslib/io/`) — I/O utilities
  - crates.io: https://crates.io/crates/anki_io
  - docs.rs: https://docs.rs/anki_io/
- **anki_i18n** (`rslib/i18n/`) — i18n support
  - crates.io: https://crates.io/crates/anki_i18n
  - docs.rs: https://docs.rs/anki_i18n/
- **anki_proto** (`rslib/proto/`) — protobuf types used by Anki
  - crates.io: https://crates.io/crates/anki_proto
  - docs.rs: https://docs.rs/anki_proto/
- **anki_proto_gen** (`rslib/proto_gen/`) — codegen helpers used at build time
  - crates.io: https://crates.io/crates/anki_proto_gen
  - docs.rs: https://docs.rs/anki_proto_gen/
