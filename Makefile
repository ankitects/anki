PREFIX := /usr
SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.ONESHELL:
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules
RUNARGS :=
.SUFFIXES:
BLACKARGS := -t py36 anki aqt
RUSTARGS := --release --strip

$(shell mkdir -p .build)

# Installing
######################

.PHONY: all install uninstall

all:
	@echo "You can run Anki from this folder with 'make run'."
	@echo
	@echo "After confirming it's working, to install Anki system-wide, use"
	@echo "'make build && sudo make install'."
	@echo
	@echo "To undo a system install, use 'sudo make uninstall'."

install:
	rm -rf ${DESTDIR}${PREFIX}/share/anki
	mkdir -p ${DESTDIR}${PREFIX}/share/anki
	cp -av anki aqt web ${DESTDIR}${PREFIX}/share/anki/
	-cp -av locale ${DESTDIR}${PREFIX}/share/anki/
	sed -e 's:@PREFIX@:${PREFIX}:' tools/runanki.system.in > tools/runanki.system
	install -m 0755 -D tools/runanki.system ${DESTDIR}${PREFIX}/bin/anki
	install -m 0644 -D -t ${DESTDIR}${PREFIX}/share/pixmaps anki.xpm anki.png
	install -m 0644 -D -t ${DESTDIR}${PREFIX}/share/applications anki.desktop
	install -m 0644 -D -t ${DESTDIR}${PREFIX}/share/man/man1 anki.1
	install -m 0644 -D -t ${DESTDIR}${PREFIX}/share/doc/anki README.contributing README.development README.md LICENSE LICENSE.logo
	-xdg-mime install anki.xml --novendor
	-xdg-mime default anki.desktop application/x-anki
	-xdg-mime default anki.desktop application/x-apkg
	@echo
	@echo "Install complete."
	# fixme: _ankirs.so needs to be copied into system python env or
	# 'maturin build' used

uninstall:
	rm -rf ${DESTDIR}${PREFIX}/share/anki
	rm -rf ${DESTDIR}${PREFIX}/bin/anki
	rm -rf ${DESTDIR}${PREFIX}/share/pixmaps/anki.xpm
	rm -rf ${DESTDIR}${PREFIX}/share/pixmaps/anki.png
	rm -rf ${DESTDIR}${PREFIX}/share/applications/anki.desktop
	rm -rf ${DESTDIR}${PREFIX}/share/man/man1/anki.1
	-xdg-mime uninstall ${DESTDIR}${PREFIX}/share/mime/packages/anki.xml
	@echo
	@echo "Uninstall complete."

# Prerequisites
######################

RUNREQS := .build/py-run-deps .build/ts-deps

# Python prerequisites
######################

.build/py-run-deps: requirements.txt
	pip install -r $<
	@touch $@

.build/py-check-reqs: requirements.check .build/py-run-deps
	pip install -r $<
	./tools/typecheck-setup.sh
	@touch $@

# TS prerequisites
######################

.build/ts-deps: ts/package.json
	(cd ts && npm i)
	@touch $@

# Rust prerequisites
######################

.build/rust-deps: .build/py-run-deps
	pip install maturin
	@touch $@

RUST_TOOLCHAIN := $(shell cat rs/rust-toolchain)

.build/rs-fmt-deps:
	rustup component add rustfmt-preview --toolchain $(RUST_TOOLCHAIN)
	@touch $@

.build/rs-clippy-deps:
	rustup component add clippy-preview --toolchain $(RUST_TOOLCHAIN)
	@touch $@

# Protobuf
######################

PROTODEPS := $(wildcard proto/*.proto)

# Typescript source
######################

TSDEPS := $(wildcard ts/src/*.ts)
JSDEPS := $(patsubst ts/src/%.ts, web/%.js, $(TSDEPS))

# Rust source
######################

RSDEPS := $(shell find rs -type f | grep -v target)

# Building
######################

BUILDDEPS := .build/ui .build/js .build/rs .build/py-proto

.build/ui: $(RUNREQS) $(shell find designer -type f)
	./tools/build_ui.sh
	@touch $@

.build/js: .build/ts-deps $(TSDEPS)
	(cd ts && npm run build)
	@touch $@

.build/rs: .build/rust-deps $(RUNREQS) $(RSDEPS) $(PROTODEPS)
	(cd rs/pybridge && maturin develop $(RUSTARGS))
	@touch $@

.build/py-proto: $(RUNREQS) $(PROTODEPS)
	protoc --proto_path=proto --python_out=anki proto/bridge.proto
	@touch $@

.PHONY: build clean

build: $(BUILDDEPS)

.PHONY: clean
clean:
	rm -rf .build
	rm -rf $(JSDEPS)
	rm -rf rs/target

# Running
######################

.PHONY: run
run: build
	./runanki ${RUNARGS}

# Checking
######################

.PHONY: check
check: rs-test rs-fmt rs-clippy py-mypy py-test py-fmt py-imports py-lint ts-fmt

.PHONY: fix
fix: fix-py-fmt fix-py-imports fix-rs-fmt fix-ts-fmt

# Checking python
######################

PYCHECKDEPS := $(BUILDDEPS) .build/py-check-reqs $(shell find anki aqt -name '*.py' | grep -v buildhash.py)

.build/py-mypy: $(PYCHECKDEPS)
	mypy anki aqt
	@touch $@

.build/py-test: $(PYCHECKDEPS) $(wildcard tests/*.py)
	./tools/tests.sh
	@touch $@

.build/py-lint: $(PYCHECKDEPS)
	pylint -j 0 --rcfile=.pylintrc -f colorized --extension-pkg-whitelist=PyQt5,_ankirs anki aqt
	@touch $@

.build/py-imports: $(PYCHECKDEPS)
	isort anki aqt --check # if this fails, run 'make fix-py-imports'
	@touch $@

.build/py-fmt: $(PYCHECKDEPS)
	black --check $(BLACKARGS) # if this fails, run 'make fix-py-fmt'
	@touch $@

.PHONY: py-mypy py-test py-lint py-imports py-fmt
py-mypy: .build/py-mypy
py-test: .build/py-test
py-lint: .build/py-lint
py-imports: .build/py-imports
py-fmt: .build/py-fmt

.PHONY: fix-py-imports fix-py-fmt

fix-py-imports:
	isort anki aqt

fix-py-fmt:
	black $(BLACKARGS) anki aqt

# Checking rust
######################

.build/rs-test: $(RSDEPS)
	(cd rs/ankirs && cargo test)
	@touch $@

.build/rs-fmt: .build/rs-fmt-deps $(RSDEPS)
	(cd rs && cargo fmt -- --check) # if this fails, run 'make fix-rs-fmt'
	@touch $@

.build/rs-clippy: .build/rs-clippy-deps $(RSDEPS)
	(cd rs && cargo clippy -- -D warnings)
	@touch $@

.PHONY: rs-test rs-fmt fix-rs-fmt rs-clippy

rs-test: .build/rs-test
rs-fmt: .build/rs-fmt
rs-clippy: .build/rs-clippy

fix-rs-fmt:
	(cd rs && cargo fmt)


# Checking typescript
######################

TSCHECKDEPS := $(BUILDDEPS) $(TSDEPS)

.build/ts-fmt: $(TSCHECKDEPS)
	(cd ts && npm run check-pretty) # if this fails, run 'make fix-ts-fmt'
	@touch $@

.PHONY: fix-ts-fmt ts-fmt
ts-fmt: .build/ts-fmt

fix-ts-fmt:
	(cd ts && npm run pretty)

