SHELL := /bin/bash

ifndef SHELLFLAGS
	SHELLFLAGS :=
endif

.SHELLFLAGS := -eu -o pipefail ${SHELLFLAGS} -c
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules

ifndef OS
	OS := unknown
endif

.DELETE_ON_ERROR:
.SUFFIXES:

$(shell mkdir -p .build)

PHONY: all
all: check

.build/npm: package.json
	npm i
	@touch $@

PROTODEPS := ../proto/backend.proto ../proto/fluent.proto
BUILDDEPS := .build/npm webpack.config.js

.build/proto: $(BUILDDEPS) $(PROTODEPS)
	npm run proto
	@touch $@

PHONY: dev
dev: .build/proto
	npm run dev

PHONY: build
build: .build/build

.build/build: .build/proto $(BUILDDEPS) $(wildcard src/*/*.svelte src/*/*.ts)
	npm run build
	@touch $@

.PHONY: check
check:
	npm run check

.PHONY: fix
fix:
	npm run fix
