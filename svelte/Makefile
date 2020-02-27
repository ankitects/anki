SHELL := /bin/bash

.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules
.SUFFIXES:

$(shell mkdir -p .build)

.PHONY: all
all: run

.PHONY: run
run: build-tslib
	tmux new -d -s anki-react "make run-pybackend"
	tmux split-window "make run-svelte"
	tmux attach

.PHONY: run-pybackend
run-pybackend:
	../pyenv/bin/pip install bottle
	../pyenv/bin/python ../react/pybackend.py

.PHONY: build-tslib
build-tslib:
	(cd ../tslib && make build)

.PHONY: run-svelte
run-svelte: .build/deps
	npm run dev

.build/deps: package.json
	npm i
	@touch $@

.PHONY: svelete-lint
svelte-lint:
	npx --no-install svelte-type-checker
