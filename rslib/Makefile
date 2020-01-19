SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules

$(shell mkdir -p .build)

.PHONY: all check fix clean

all: check

check: .build/check

fix:
	cargo fmt

clean:
	rm -rf .build target

PROTO_SOURCE := $(wildcard ../proto/*.proto)
RS_SOURCE := $(wildcard src/*)
ALL_SOURCE := $(RS_SOURCE) $(PROTO_SOURCE)

# nightly currently required for ignoring files in rustfmt.toml
RUST_TOOLCHAIN := $(shell cat rust-toolchain)

.build/rs-tools: rust-toolchain
	rustup toolchain install $(RUST_TOOLCHAIN)
	rustup component add rustfmt-preview --toolchain $(RUST_TOOLCHAIN)
	rustup component add clippy-preview --toolchain $(RUST_TOOLCHAIN)
	@touch $@

# set TZ here for the benefit of a single unit test that fails
# when running in the GitHub workflow.
.build/check: .build/rs-tools $(ALL_SOURCE)
	TZ="Australia/Perth" cargo test --lib -- --nocapture
	cargo fmt -- --check
	cargo clippy -- -D warnings
	@touch $@
