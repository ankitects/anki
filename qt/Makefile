SHELL := /bin/bash
FIND := $(if $(wildcard /bin/find),/bin/find,/usr/bin/find)

.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules
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

.build/ui: $(shell ${FIND} designer -type f)
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
	black aqt/gui_hooks.py
	@touch $@

BUILD_STEPS := .build/run-deps .build/dev-deps .build/js .build/ui aqt/buildinfo.py .build/hooks .build/i18n

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

CHECKDEPS := $(shell ${FIND} aqt tests -name '*.py' | grep -v buildinfo.py)

.build/mypy: $(CHECKDEPS) .build/qt-stubs
	mypy aqt
	@touch $@

.build/test: $(CHECKDEPS)
	python -m pytest -s
	@touch $@

.build/lint: $(CHECKDEPS)
	pylint -j 0 --rcfile=.pylintrc -f colorized --extension-pkg-whitelist=PyQt5,ankirspy aqt tests setup.py
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

.PHONY: build
build: $(BUILD_STEPS)
	rm -rf dist build
	python setup.py -q bdist_wheel
	rsync -a dist/*.whl ../dist/

.PHONY: develop
develop: $(BUILD_STEPS)

aqt/buildinfo.py: ../meta/version ../meta/buildhash
	echo "buildhash='$$(cat ../meta/buildhash)'" > $@
	echo "version='$$(cat ../meta/version)'" >> $@
