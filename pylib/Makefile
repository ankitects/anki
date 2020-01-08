SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules
RUNARGS :=
.SUFFIXES:
BLACKARGS := -t py36 anki tests setup.py --exclude='backend_pb2|buildinfo'
ISORTARGS := anki tests setup.py

$(shell mkdir -p .build ../dist)

PHONY: all
all: check

.build/run-deps: setup.py
	pip install -e .
	@touch $@

.build/dev-deps: requirements.dev
	pip install -r requirements.dev
	@touch $@

PROTODEPS := $(wildcard ../proto/*.proto)

.build/py-proto: .build/dev-deps $(PROTODEPS)
	protoc --proto_path=../proto --python_out=anki --mypy_out=anki $(PROTODEPS)
	@touch $@

BUILD_STEPS := .build/run-deps .build/dev-deps .build/py-proto anki/buildinfo.py

# Checking
######################

.PHONY: check
check: $(BUILD_STEPS) .build/mypy .build/test .build/fmt .build/imports .build/lint

.PHONY: fix
fix: $(BUILD_STEPS)
	isort $(ISORTARGS)
	black $(BLACKARGS)

.PHONY: clean
clean:
	rm -rf .build anki.egg-info build dist

# Checking python
######################

CHECKDEPS := $(shell find anki tests -name '*.py' | grep -v buildinfo.py)

.build/mypy: $(CHECKDEPS)
	mypy anki
	@touch $@

.build/test: $(CHECKDEPS)
	python -m pytest --durations=1 -s
	@touch $@

.build/lint: $(CHECKDEPS)
	pylint -j 0 --rcfile=.pylintrc -f colorized --extension-pkg-whitelist=ankirspy anki tests setup.py
	@touch $@

.build/imports: $(CHECKDEPS)
	isort $(ISORTARGS) --check
	@touch $@

.build/fmt: $(CHECKDEPS)
	black --check $(BLACKARGS)
	@touch $@

# Building
######################

# we only want the wheel when building, but passing -f wheel to poetry
# breaks the inclusion of files listed in pyproject.toml
.PHONY: build
build: $(BUILD_STEPS) $(CHECKDEPS)
	rm -rf dist
	python setup.py -q bdist_wheel
	rsync -a dist/*.whl ../dist/

# prepare code for running in place
.PHONY: develop
develop: $(BUILD_STEPS)

anki/buildinfo.py: ../meta/version ../meta/buildhash
	echo "buildhash='$$(cat ../meta/buildhash)'" > $@
	echo "version='$$(cat ../meta/version)'" >> $@
