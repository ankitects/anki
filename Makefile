SHELL := bash
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
	python3 -m venv pyenv && \
	. pyenv/bin/activate && \
	pip install --upgrade pip setuptools && \
	python -c 'import PyQt5' 2>/dev/null || pip install -r qt/requirements.qt

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
	@set -eo pipefail && \
	. pyenv/bin/activate && \
	for dir in $(DEVEL); do \
		$(SUBMAKE) -C $$dir develop BUILDFLAGS="$(BUILDFLAGS)"; \
	done

.PHONY: run
run: develop
	@set -eo pipefail && \
	. pyenv/bin/activate && \
	echo "Starting Anki..."; \
	qt/runanki $(RUNFLAGS)

.PHONY: build
build: clean-dist build-rspy build-pylib build-qt add-buildhash
	@echo
	@echo "Build complete."

.PHONY: build-rspy
build-rspy: pyenv buildhash
	@. pyenv/bin/activate && \
	$(SUBMAKE) -C rspy build BUILDFLAGS="$(BUILDFLAGS)"

.PHONY: build-pylib
build-pylib:
	@. pyenv/bin/activate && \
	$(SUBMAKE) -C pylib build

.PHONY: build-qt
build-qt:
	@. pyenv/bin/activate && \
	$(SUBMAKE) -C qt build

.PHONY: clean
clean: clean-dist
	@set -eo pipefail && \
	for dir in $(DEVEL); do \
	  $(SUBMAKE) -C $$dir clean; \
	done

.PHONY: clean-dist
clean-dist:
	rm -rf dist

.PHONY: check
check: pyenv buildhash
	@set -eo pipefail && \
	for dir in $(CHECKABLE_RS); do \
	  $(SUBMAKE) -C $$dir check; \
	done; \
	. pyenv/bin/activate && \
	$(SUBMAKE) -C rspy develop && \
	$(SUBMAKE) -C pylib develop && \
	for dir in $(CHECKABLE_PY); do \
	  $(SUBMAKE) -C $$dir check; \
	done;
	@echo
	@echo "All checks passed!"

.PHONY: fix
fix:
	@set -eo pipefail && \
	. pyenv/bin/activate && \
	for dir in $(CHECKABLE_RS) $(CHECKABLE_PY); do \
	  $(SUBMAKE) -C $$dir fix; \
	done; \

.PHONY: add-buildhash
add-buildhash:
	@ver=$$(cat meta/version); \
	hash=$$(cat meta/buildhash); \
	rename "s/-$${ver}-/-$${ver}+$${hash}-/" dist/*-$$ver-*
