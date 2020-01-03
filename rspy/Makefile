SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules

$(shell mkdir -p .build ../dist)

OUTDIR := ../dist
BUILDFLAGS := --release --strip

.PHONY: all develop build check fix clean

all: develop

develop: .build/tools .build/vernum
	maturin develop $(BUILDFLAGS)

build: .build/tools .build/vernum
	rm -rf $(OUTDIR)/ankirspy*
	maturin build -i $(shell which python3) -o $(OUTDIR) $(BUILDFLAGS)

check: .build/check

fix:
	cargo fmt

clean:
	rm -rf .build target

# nightly currently required for ignoring files in rustfmt.toml
RUST_TOOLCHAIN := $(shell cat rust-toolchain)

.build/tools: requirements.txt rust-toolchain
	pip install -r requirements.txt
	rustup toolchain install $(RUST_TOOLCHAIN)
	rustup component add rustfmt-preview --toolchain $(RUST_TOOLCHAIN)
	rustup component add clippy-preview --toolchain $(RUST_TOOLCHAIN)
	@touch $@

.build/check: build
	cargo fmt -- --check
	cargo clippy -- -D warnings
	@touch $@

VER := $(shell cat ../meta/version)
.build/vernum: ../meta/version
	sed -i.bak 's/.*automatically updated.*/version = "$(VER)" # automatically updated/' Cargo.toml
	rm Cargo.toml.bak
	@touch $@
