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

PROTODEPS := ../proto/backend.proto ../proto/fluent.proto
BUILDDEPS := .build/npm webpack.config.js

.build/proto: $(BUILDDEPS) $(PROTODEPS)
	npx pbjs -t json-module -w es6 ../proto/backend.proto -o src/backend/proto.js
	npx pbjs -t static-module ../proto/backend.proto | npx pbts -o src/backend/proto.d.ts -
	@touch $@

PHONY: dev
dev: .build/proto
	npm run dev

PHONY: build
build: .build/build

PHONY: develop
develop: .build/build

.build/build: .build/proto $(BUILDDEPS) $(wildcard src/*/*.svelte src/*/*.ts src/*/*.scss)
	npm run build
	@touch $@

.build/npm: package.json
	npm i
	touch $@

.PHONY: check
check: .build/proto
	npm run check

.PHONY: fix
fix: .build/build
	npm run fix

.PHONY: clean
clean:
	rm -rf .build node_modules src/backend/*
