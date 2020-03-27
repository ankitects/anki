SHELL := /bin/bash

ifndef SHELLFLAGS
	SHELLFLAGS :=
endif

.SHELLFLAGS := -eu -o pipefail ${SHELLFLAGS} -c
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules

RUNARGS :=
MYPY_ARGS :=
FIND := $(if $(wildcard /bin/find),/bin/find,/usr/bin/find)

ifndef OS
	OS := unknown
endif

# https://anki.tenderapp.com/discussions/beta-testing/1860-error-unused-type-ignore-comment
ifneq (${OS},Windows_NT)
	MYPY_ARGS := --warn-unused-ignores
endif

.DELETE_ON_ERROR:
.SUFFIXES:
BLACKARGS := -t py36 anki tests setup.py tools/*.py --exclude='_pb2|buildinfo'
ISORTARGS := anki tests setup.py

$(shell mkdir -p .build ../dist)

PHONY: all
all: check

.build/run-deps: setup.py
	python -m pip install -e .
	@touch $@

.build/dev-deps: requirements.dev
	python -m pip install -r requirements.dev
	@touch $@

PROTODEPS := $(wildcard ../proto/*.proto)

.build/py-proto: .build/dev-deps $(PROTODEPS)
	protoc --proto_path=../proto --python_out=anki --mypy_out=anki $(PROTODEPS)
	perl -i'' -pe 's/from fluent_pb2/from anki.fluent_pb2/' anki/backend_pb2.pyi
	perl -i'' -pe 's/import fluent_pb2/import anki.fluent_pb2/' anki/backend_pb2.py
	@touch $@

.build/hooks: tools/genhooks.py tools/hookslib.py
	python tools/genhooks.py
	python -m black anki/hooks.py
	@touch $@

BUILD_STEPS := .build/run-deps .build/dev-deps .build/py-proto anki/buildinfo.py .build/hooks

# Checking
######################

.PHONY: check
check: $(BUILD_STEPS) .build/mypy .build/test .build/fmt .build/imports .build/lint

.PHONY: fix
fix: $(BUILD_STEPS)
	isort $(ISORTARGS)
	python -m black $(BLACKARGS)

.PHONY: clean
clean:
	rm -rf .build anki.egg-info build dist

# Checking python
######################

CHECKDEPS := $(shell ${FIND} anki tests -name '*.py' | grep -v buildinfo.py)

.build/mypy: $(CHECKDEPS)
	python -m mypy ${MYPY_ARGS} anki
	@touch $@

.build/test: $(CHECKDEPS)
	python -m pytest --durations=1 -s
	@touch $@

.build/lint: $(CHECKDEPS)
	python -m pylint -j 0 --rcfile=.pylintrc -f colorized \
		--extension-pkg-whitelist=ankirspy anki tests setup.py
	@touch $@

.build/imports: $(CHECKDEPS)
	isort $(ISORTARGS) --check
	@touch $@

.build/fmt: $(CHECKDEPS)
	python -m black --check $(BLACKARGS)
	@touch $@

# Building
######################

.PHONY: build
build: $(BUILD_STEPS) $(CHECKDEPS)
	rm -rf dist build
	python setup.py -q bdist_wheel
	rsync -a dist/*.whl ../dist/

# prepare code for running in place
.PHONY: develop
develop: $(BUILD_STEPS)

anki/buildinfo.py: ../meta/version ../meta/buildhash
	echo "buildhash='$$(cat ../meta/buildhash)'" > $@
	echo "version='$$(cat ../meta/version)'" >> $@
