SHELL := /bin/bash

ifndef SHELLFLAGS
	SHELLFLAGS :=
endif

.SHELLFLAGS := -eu -o pipefail ${SHELLFLAGS} -c
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules

FIND := $(if $(wildcard /bin/find),/bin/find,/usr/bin/find)
.DELETE_ON_ERROR:

$(shell mkdir -p .build)

.PHONY: all check fix clean develop

all: check

check: .build/check

fix:
	cargo fmt

clean:
	rm -rf .build target

develop: .build/vernum ftl/repo

ftl/repo:
	(cd ftl && ./scripts/fetch-latest-translations)

ALL_SOURCE := $(shell "${FIND}" src -type f | egrep -v "i18n/autogen|i18n/ftl|_proto.rs") $(wildcard ftl/*.ftl)

# nightly currently required for ignoring files in rustfmt.toml
RUST_TOOLCHAIN := $(shell cat rust-toolchain)

.build/rs-tools: rust-toolchain
	rustup toolchain install $(RUST_TOOLCHAIN)
	rustup component add rustfmt-preview --toolchain $(RUST_TOOLCHAIN)
	rustup component add clippy-preview --toolchain $(RUST_TOOLCHAIN)
	@touch $@

.build/check: ftl/repo .build/rs-tools $(ALL_SOURCE)
	cargo test --lib -- --nocapture
	cargo fmt -- --check
	cargo clippy -- -D warnings
	@touch $@

VER := $(shell cat ../meta/version)
.build/vernum: ../meta/version
	sed -i.bak 's/.*automatically updated.*/version = "$(VER)"  # automatically updated/' Cargo.toml
	rm Cargo.toml.bak
	@touch $@
