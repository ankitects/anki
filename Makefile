SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules
SUBMAKE := $(MAKE) --print-directory
.SUFFIXES:

BUILDFLAGS := --release --strip
RUNFLAGS :=
CHECKABLE := rslib pylib qt
DEVEL := rspy pylib qt

# - modern pip required for wheel
# - add qt if missing
pyenv:
	python3 -m venv pyenv && \
	. pyenv/bin/activate && \
	pip install --upgrade pip setuptools && \
	python -c 'import PyQt5' 2>/dev/null || pip install -r qt/requirements.qt

# update build hash
meta/buildhash:
	oldhash=$$(test -f meta/buildhash && cat meta/buildhash || true); \
	newhash=$$(git rev-parse --short HEAD); \
	if [ "$$oldhash" != "$$newhash" ]; then \
		echo $$newhash > meta/buildhash; \
	fi

.PHONY: run
run: pyenv meta/buildhash
	@. pyenv/bin/activate && \
	for dir in $(DEVEL); do \
	  $(SUBMAKE) -C $$dir develop BUILDFLAGS="$(BUILDFLAGS)"; \
	done; \
	echo "Starting Anki..."; \
	qt/runanki $(RUNFLAGS)

.PHONY: build
build: pyenv meta/buildhash
	@. pyenv/bin/activate && \
	for dir in $(DEVEL); do \
	  $(SUBMAKE) -C $$dir build BUILDFLAGS="$(BUILDFLAGS)"; \
	done; \
	helpers/rename-with-buildhash
	@echo
	@echo "Build complete."

.PHONY: clean
clean:
	rm -rf dist
	@for dir in $(DEVEL); do \
	  $(SUBMAKE) -C $$dir clean; \
	done

.PHONY: check
check: pyenv meta/buildhash
	@. pyenv/bin/activate && \
	for dir in $(CHECKABLE); do \
	  $(SUBMAKE) -C $$dir check; \
	done;
	@echo
	@echo "All checks passed!"

.PHONY: fix
fix:
	for dir in $(CHECKABLE); do \
	  $(SUBMAKE) -C $$dir fix; \
	done; \
