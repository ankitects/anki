PREFIX := /usr
SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules
.SUFFIXES:

BLACKARGS := -t py36 aqt tests --exclude=aqt/forms
ISORTARGS := aqt tests

$(shell mkdir -p .build ../build)

PHONY: all
all: check

.build/run-deps: setup.py
	pip install -e .
	@touch $@

.build/dev-deps: requirements.dev
	pip install -r requirements.dev
	@touch $@

.build/ui: $(shell find designer -type f)
	./tools/build_ui.sh
	@touch $@

TSDEPS := $(wildcard ts/src/*.ts)

.build/js: $(TSDEPS)
	(cd ts && npm i && npm run build)
	@touch $@

BUILD_STEPS := .build/run-deps .build/dev-deps .build/js .build/ui

# Checking
######################

.PHONY: check
check: $(BUILD_STEPS) .build/mypy .build/test .build/fmt .build/imports .build/lint .build/ts-fmt

.PHONY: fix
fix: $(BUILD_STEPS)
	isort $(ISORTARGS)
	black $(BLACKARGS)
	(cd ts && npm run pretty)

.PHONY: clean
clean:
	rm -rf .build aqt.egg-info build dist

# Checking Typescript
######################

JSDEPS := $(patsubst ts/src/%.ts, web/%.js, $(TSDEPS))

.build/ts-fmt: $(TSDEPS)
	(cd ts && npm i && npm run check-pretty)
	@touch $@

# Checking python
######################

PYLIB := ../pylib

CHECKDEPS := $(shell find aqt tests -name '*.py')

.build/mypy: $(CHECKDEPS) .build/qt-stubs
	MYPYPATH=$(PYLIB) mypy aqt
	@touch $@

.build/test: $(CHECKDEPS)
	python -m nose2 --plugin=nose2.plugins.mp -N 16
	@touch $@

.build/lint: $(CHECKDEPS)
	pylint -j 0 --rcfile=.pylintrc -f colorized --extension-pkg-whitelist=PyQt5,ankirspy aqt
	@touch $@

.build/imports: $(CHECKDEPS)
	isort $(ISORTARGS) --check
	@touch $@

.build/fmt: $(CHECKDEPS)
	black --check $(BLACKARGS)
	@touch $@

.build/qt-stubs:
	./tools/typecheck-setup.sh
	@touch $@

# Building
######################

# we only want the wheel when building, but passing -f wheel to poetry
# breaks the inclusion of files listed in pyproject.toml
.PHONY: build
build: $(BUILD_STEPS)
	rm -rf dist
	python setup.py bdist_wheel
	rsync -a dist/*.whl ../build/

.PHONY: develop
develop: $(BUILD_STEPS)
