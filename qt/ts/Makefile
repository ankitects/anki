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

BIN := ../../ts/node_modules/.bin

PHONY: all
all: check

PHONY: build
build:
	$(BIN)/tsc --build
	$(BIN)/sass --no-source-map scss:../aqt_data/web

.PHONY: check
check: devDependencies
	$(BIN)/prettier --check src/*.ts
	npm run test

devDependencies: package.json
# 	https://github.com/fsevents/fsevents/issues/321
	npm install --no-optional --only=dev

.PHONY: fix
fix:
	$(BIN)/prettier --write src/*.ts
