SHELL := /bin/bash

ifndef SHELLFLAGS
	SHELLFLAGS :=
endif

.SHELLFLAGS := -eu -o pipefail ${SHELLFLAGS} -c
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules

FIND := $(if $(wildcard /bin/find),/bin/find,/usr/bin/find)
MYPY_ARGS :=
PYLINT_ARGS :=

ifndef OS
	OS := unknown
endif

# https://anki.tenderapp.com/discussions/beta-testing/1860-error-unused-type-ignore-comment
ifneq (${OS},Windows_NT)
	MYPY_ARGS := --warn-unused-ignores
else
	PYLINT_ARGS := --ignored-modules=win32file,pywintypes,socket,win32pipe
endif

.DELETE_ON_ERROR:
.SUFFIXES:

BLACKARGS := -t py36 aqt tests setup.py tools/*.py --exclude='aqt/forms|buildinfo|colors'
ISORTARGS := aqt tests setup.py

$(shell mkdir -p .build ../dist)

PHONY: all
all: check

.build/run-deps: setup.py
	python -m pip install -e .
	@touch $@

.build/dev-deps: requirements.dev
	python -m pip install -r requirements.dev
	@touch $@

.build/ui: $(shell "${FIND}" designer -type f)
	./tools/build_ui.sh
	@touch $@

.build/i18n: $(wildcard po/repo/desktop/*/anki.po)
	(cd po && \
	./scripts/build-mo-files && \
	./scripts/copy-qt-files)
	@touch $@

TSDEPS := $(wildcard ts/src/*.ts) $(wildcard ts/scss/*.scss)

.build/js: $(TSDEPS)
	(cd ts && npm i && npm run build)
	python ./tools/extract_scss_colors.py
	@touch $@

.build/hooks: tools/genhooks_gui.py ../pylib/tools/hookslib.py
	python tools/genhooks_gui.py
	python -m black aqt/gui_hooks.py
	@touch $@

BUILD_STEPS := .build/vernum .build/run-deps .build/dev-deps .build/js .build/ui aqt/buildinfo.py .build/hooks .build/i18n

# Checking
######################

.PHONY: check
check: $(BUILD_STEPS) .build/mypy .build/test .build/fmt .build/imports .build/lint .build/ts-fmt

.PHONY: fix
fix: $(BUILD_STEPS)
	isort $(ISORTARGS)
	python -m black $(BLACKARGS)
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

CHECKDEPS := $(shell "${FIND}" aqt tests -name '*.py' | grep -v buildinfo.py)

.build/mypy: $(CHECKDEPS) .build/qt-stubs
	python -m mypy ${MYPY_ARGS} aqt
	@touch $@

.build/test: $(CHECKDEPS)
	python -m pytest -s
	@touch $@

.build/lint: $(CHECKDEPS)
	python -m pylint -j 0 --rcfile=.pylintrc -f colorized ${PYLINT_ARGS} \
		--extension-pkg-whitelist=PyQt5,ankirspy aqt tests setup.py
	@touch $@

.build/imports: $(CHECKDEPS)
	isort $(ISORTARGS) --check
	@touch $@

.build/fmt: $(CHECKDEPS)
	python -m black --check $(BLACKARGS)
	@touch $@

.build/qt-stubs:
	./tools/typecheck-setup.sh
	@touch $@

# Building
######################

.PHONY: build
build: .build/build

.build/build: $(BUILD_STEPS) $(CHECKDEPS)
	rm -rf dist build
	python setup.py -q bdist_wheel
	rsync -a dist/*.whl ../dist/
	touch $@

.PHONY: develop
develop: $(BUILD_STEPS)

aqt/buildinfo.py: ../meta/version ../meta/buildhash
	echo "buildhash='$$(cat ../meta/buildhash)'" > $@
	echo "version='$$(cat ../meta/version)'" >> $@

VER := $(shell cat ../meta/version)
.build/vernum: ../meta/version
	sed -i.bak 's/.*automatically updated 1.*/    "anki==$(VER)",  # automatically updated 1/' setup.py
	sed -i.bak 's/.*automatically updated 2.*/    version="$(VER)",  # automatically updated 2/' setup.py
	rm setup.py.bak
	@touch $@
