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

ifeq (${OS},Windows_NT)
	# Windows terminal is confusing it with its `cmd` builtin `rename` command
	ifndef RENAME_BIN
		RENAME_BIN := perl rename
	endif

	ifndef ACTIVATE_SCRIPT
		ACTIVATE_SCRIPT := pyenv/Scripts/activate
	endif

	ifndef PYTHON_BIN
		PYTHON_BIN := python
	endif
else
	ifndef RENAME_BIN
		RENAME_BIN := rename
	endif

	ifndef ACTIVATE_SCRIPT
		ACTIVATE_SCRIPT := pyenv/bin/activate
	endif

	ifndef PYTHON_BIN
		PYTHON_BIN := python3
	endif
endif

ifndef ANKI_EXTRA_PIP
	ANKI_EXTRA_PIP := true
endif

.DELETE_ON_ERROR:
SUBMAKE := $(MAKE) --print-directory
.SUFFIXES:

BUILDFLAGS := --release --strip
DEVFLAGS := $(BUILDFLAGS)
RUNFLAGS :=
CHECKABLE_PY := pylib qt
CHECKABLE_RS := rslib
DEVEL := rslib rspy pylib qt

.PHONY: all
all: run

# - modern pip required for wheel
# - add qt if missing
pyenv:
	"${PYTHON_BIN}" -m ensurepip && \
	"${PYTHON_BIN}" -m pip install virtualenv && \
	"${PYTHON_BIN}" -m venv pyenv && \
	case "$$(uname -s)" in CYGWIN*|MINGW*|MSYS*) \
		dos2unix "${ACTIVATE_SCRIPT}" && \
		sed -i -- "s@VIRTUAL_ENV=\".*\"@VIRTUAL_ENV=\"$(shell pwd)/pyenv\"@g" "${ACTIVATE_SCRIPT}" \
		;; esac; \
	. "${ACTIVATE_SCRIPT}" && \
	python --version && \
	python -m pip install --upgrade pip setuptools && \
	${ANKI_EXTRA_PIP} && \
	python -c 'import PyQt5' 2>/dev/null || python -m pip install -r qt/requirements.qt

# update build hash
.PHONY: buildhash
buildhash:
	@oldhash=$$(test -f meta/buildhash && cat meta/buildhash || true) && \
	newhash=$$(git rev-parse --short=8 HEAD || echo dev) && \
	if [ "$$oldhash" != "$$newhash" ]; then \
		echo $$newhash > meta/buildhash; \
	fi

.PHONY: develop
develop: pyenv buildhash prepare
	@. "${ACTIVATE_SCRIPT}" && \
	for dir in $(DEVEL); do \
		$(SUBMAKE) -C $$dir develop DEVFLAGS="$(DEVFLAGS)"; \
	done

.PHONY: run
run: develop
	@. "${ACTIVATE_SCRIPT}" && \
	echo "Starting Anki..." && \
	python qt/runanki $(RUNFLAGS)

.PHONY: prepare
prepare: rslib/ftl/repo qt/ftl/repo qt/po/repo

rslib/ftl/repo:
	$(MAKE) pull-i18n
qt/ftl/repo:
	$(MAKE) pull-i18n
qt/po/repo:
	$(MAKE) pull-i18n

.PHONY: build
build: clean-dist build-rspy build-pylib build-qt add-buildhash
	@echo
	@echo "Build complete."

.PHONY: build-rspy
build-rspy: pyenv buildhash
	@. "${ACTIVATE_SCRIPT}" && \
	$(SUBMAKE) -C rspy build BUILDFLAGS="$(BUILDFLAGS)"

.PHONY: build-pylib
build-pylib:
	@. "${ACTIVATE_SCRIPT}" && \
	$(SUBMAKE) -C pylib build

.PHONY: build-qt
build-qt:
	@. "${ACTIVATE_SCRIPT}" && \
	$(SUBMAKE) -C qt build

.PHONY: clean
clean: clean-dist
	@for dir in $(DEVEL); do \
	  $(SUBMAKE) -C $$dir clean; \
	done

.PHONY: clean-dist
clean-dist:
	rm -rf dist

.PHONY: check
check: pyenv buildhash prepare
	@.github/scripts/trailing-newlines.sh && \
	for dir in $(CHECKABLE_RS); do \
	  $(SUBMAKE) -C $$dir check; \
	done && \
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
	@. "${ACTIVATE_SCRIPT}" && \
	for dir in $(CHECKABLE_RS) $(CHECKABLE_PY); do \
	  $(SUBMAKE) -C $$dir fix; \
	done; \

.PHONY: add-buildhash
add-buildhash:
	@if [[ ! -f rename ]]; then \
		curl -LO https://raw.githubusercontent.com/subogero/rename/master/rename; \
	fi && \
	ver="$$(cat meta/version)" && \
	hash="$$(cat meta/buildhash)" && \
	${RENAME_BIN} "s/-$${ver}-/-$${ver}+$${hash}-/" dist/*-$$ver-*


.PHONY: pull-i18n
pull-i18n:
	(cd rslib/ftl && scripts/fetch-latest-translations)
	(cd qt/ftl && scripts/fetch-latest-translations)
	(cd qt/po && scripts/fetch-latest-translations)

.PHONY: push-i18n-ftl
push-i18n-ftl: pull-i18n
	(cd rslib/ftl && scripts/upload-latest-templates)
	(cd qt/ftl && scripts/upload-latest-templates)

.PHONY: push-i18n-po
push-i18n-po: pull-i18n
	(cd qt/po && scripts/upload-latest-template)
