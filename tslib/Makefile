SHELL := /bin/bash

.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules
.SUFFIXES:

$(shell mkdir -p .build dist)

all: build

.build/deps: package.json
	npm i
	@touch $@

build: .build/deps dist/backend_pb.js
	npm run build

dist/backend_pb.js: .build/deps ../proto/backend.proto
	npm run proto

.PHONY: check
check:
	npm run lint
	npm run check-pretty
