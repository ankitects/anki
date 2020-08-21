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
	$(BIN)/sass --no-source-map scss:css
	rsync -a css/ ../aqt_data/web/

.PHONY: check
check:
	$(BIN)/prettier --check src/*.ts

.PHONY: fix
fix:
	$(BIN)/prettier --write src/*.ts
