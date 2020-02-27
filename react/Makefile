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
	tmux split-window "make run-react"
	tmux attach

.PHONY: run-pybackend
run-pybackend:
	../pyenv/bin/pip install bottle
	../pyenv/bin/python pybackend.py

.PHONY: build-tslib
build-tslib:
	(cd ../tslib && make build)

.PHONY: run-react
run-react: .build/deps
	npm run start

.build/deps: package.json
	npm i
	@touch $@
