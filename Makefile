SHELL := /bin/bash

ifeq ($(OS),Windows_NT)
	PYTHON_BIN := python
	ACTIVATE_SCRIPT := pyenv/Scripts/activate
else
	PYTHON_BIN := python3
	ACTIVATE_SCRIPT := pyenv/bin/activate
endif

.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules
SUBMAKE := $(MAKE) --print-directory
.SUFFIXES:

BUILDFLAGS := --release --strip
RUNFLAGS :=
CHECKABLE_PY := pylib qt
CHECKABLE_RS := rslib
DEVEL := rslib rspy pylib qt

.PHONY: all
all: run

# - modern pip required for wheel
# - add qt if missing
pyenv:
	"${PYTHON_BIN}" -m venv pyenv && \
	. "${ACTIVATE_SCRIPT}" && \
	python --version && \
	python -m pip install --upgrade pip setuptools && \
	${ANKI_EXTRA_PIP} && \
	python -c 'import PyQt5' 2>/dev/null || python -m pip install -r qt/requirements.qt

# update build hash
.PHONY: buildhash
buildhash:
	oldhash=$$(test -f meta/buildhash && cat meta/buildhash || true); \
	newhash=$$(git rev-parse --short=8 HEAD || echo dev); \
	if [ "$$oldhash" != "$$newhash" ]; then \
		echo $$newhash > meta/buildhash; \
	fi

.PHONY: develop
develop: pyenv buildhash
	set -eo pipefail && \
	. "${ACTIVATE_SCRIPT}" && \
	for dir in $(DEVEL); do \
		$(SUBMAKE) -C $$dir develop BUILDFLAGS="$(BUILDFLAGS)"; \
	done

.PHONY: run
run: develop
	set -eo pipefail && \
	. "${ACTIVATE_SCRIPT}" && \
	echo "Starting Anki..."; \
	python qt/runanki $(RUNFLAGS)

.PHONY: build
build: clean-dist build-rspy build-pylib build-qt add-buildhash
	@echo
	@echo "Build complete."

.PHONY: build-rspy
build-rspy: pyenv buildhash
	. "${ACTIVATE_SCRIPT}" && \
	$(SUBMAKE) -C rspy build BUILDFLAGS="$(BUILDFLAGS)"

.PHONY: build-pylib
build-pylib:
	. "${ACTIVATE_SCRIPT}" && \
	$(SUBMAKE) -C pylib build

.PHONY: build-qt
build-qt:
	. "${ACTIVATE_SCRIPT}" && \
	$(SUBMAKE) -C qt build

.PHONY: clean
clean: clean-dist
	set -eo pipefail && \
	for dir in $(DEVEL); do \
	  $(SUBMAKE) -C $$dir clean; \
	done

.PHONY: clean-dist
clean-dist:
	rm -rf dist

.PHONY: check
check: pyenv buildhash
	set -eo pipefail && \
	for dir in $(CHECKABLE_RS); do \
	  $(SUBMAKE) -C $$dir check; \
	done; \
	. "${ACTIVATE_SCRIPT}" && \
	$(SUBMAKE) -C rspy develop && \
	$(SUBMAKE) -C pylib develop && \
	for dir in $(CHECKABLE_PY); do \
	  $(SUBMAKE) -C $$dir check; \
	done;
	@echo
	@echo "All checks passed!"

.PHONY: fix
fix:
	set -eo pipefail && \
	. "${ACTIVATE_SCRIPT}" && \
	for dir in $(CHECKABLE_RS) $(CHECKABLE_PY); do \
	  $(SUBMAKE) -C $$dir fix; \
	done; \

.PHONY: add-buildhash
add-buildhash:
	ver=$$(cat meta/version); \
	hash=$$(cat meta/buildhash); \
	rename "s/-$${ver}-/-$${ver}+$${hash}-/" dist/*-$$ver-*


.PHONY: pull-i18n
pull-i18n:
	(cd rslib/ftl && scripts/fetch-latest-translations)
	(cd qt/ftl && scripts/fetch-latest-translations)
	(cd qt/i18n && ./pull-git)

.PHONY: push-i18n
push-i18n: pull-i18n
	(cd rslib/ftl && scripts/upload-latest-templates)
	(cd qt/ftl && scripts/upload-latest-templates)
	(cd qt/i18n && ./sync-po-git)
