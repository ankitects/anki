SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules

$(shell mkdir -p .build)

.PHONY: develop
develop: .build/develop

.PHONY: clean
clean:

.PHONY: build
build:

.build/develop: $(wildcard translations/anki.pot/*)
	./build-mo-files
	./copy-qt-files
	@touch $@
