PREFIX := /usr
SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.ONESHELL:
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules
RUNARGS :=
.SUFFIXES:
BLACKARGS := -t py36 anki aqt

$(shell mkdir -p .build)

# Installing
######################

.PHONY: all install uninstall

all:
	@echo "You can run Anki from this folder with 'make run'."
	@echo
	@echo "After confirming it's working, to install Anki system-wide, use"
	@echo "'make build && sudo make install'."
	@echo
	@echo "To undo a system install, use 'sudo make uninstall'."

install:
	rm -rf ${DESTDIR}${PREFIX}/share/anki
	mkdir -p ${DESTDIR}${PREFIX}/share/anki
	cp -av anki aqt web ${DESTDIR}${PREFIX}/share/anki/
	-cp -av locale ${DESTDIR}${PREFIX}/share/anki/
	sed -e 's:@PREFIX@:${PREFIX}:' tools/runanki.system.in > tools/runanki.system
	install -m 0755 -D tools/runanki.system ${DESTDIR}${PREFIX}/bin/anki
	install -m 0644 -D -t ${DESTDIR}${PREFIX}/share/pixmaps anki.xpm anki.png
	install -m 0644 -D -t ${DESTDIR}${PREFIX}/share/applications anki.desktop
	install -m 0644 -D -t ${DESTDIR}${PREFIX}/share/man/man1 anki.1
	install -m 0644 -D -t ${DESTDIR}${PREFIX}/share/doc/anki README.contributing README.development README.md LICENSE LICENSE.logo
	-xdg-mime install anki.xml --novendor
	-xdg-mime default anki.desktop application/x-anki
	-xdg-mime default anki.desktop application/x-apkg
	@echo
	@echo "Install complete."

uninstall:
	rm -rf ${DESTDIR}${PREFIX}/share/anki
	rm -rf ${DESTDIR}${PREFIX}/bin/anki
	rm -rf ${DESTDIR}${PREFIX}/share/pixmaps/anki.xpm
	rm -rf ${DESTDIR}${PREFIX}/share/pixmaps/anki.png
	rm -rf ${DESTDIR}${PREFIX}/share/applications/anki.desktop
	rm -rf ${DESTDIR}${PREFIX}/share/man/man1/anki.1
	-xdg-mime uninstall ${DESTDIR}${PREFIX}/share/mime/packages/anki.xml
	@echo
	@echo "Uninstall complete."

# Prerequisites
######################

RUNREQS := .build/pyrunreqs .build/jsreqs

.build/pyrunreqs: requirements.txt
	pip install -r $<
	touch $@

.build/pycheckreqs: requirements.check .build/pyrunreqs
	pip install -r $<
	./tools/typecheck-setup.sh
	touch $@

.build/jsreqs: ts/package.json
	(cd ts && npm i)
	touch $@

# Typescript source
######################

TSDEPS := $(wildcard ts/src/*.ts)
JSDEPS := $(patsubst ts/src/%.ts, web/%.js, $(TSDEPS))

# Building
######################

BUILDDEPS := .build/ui .build/js

.build/ui: $(RUNREQS) $(shell find designer -type f)
	./tools/build_ui.sh
	touch $@

.build/js: .build/jsreqs $(TSDEPS)
	(cd ts && npm run build)
	touch $@

.PHONY: build clean

build: $(BUILDDEPS)

.PHONY: clean
clean:
	rm -rf .build
	rm -rf $(JSDEPS)

# Running
######################

.PHONY: run
run: build
	./runanki ${RUNARGS}

# Checking
######################

.PHONY: check
check: mypy pyimports pyfmt pytest pylint checkpretty

# Checking python
######################

PYCHECKDEPS := $(BUILDDEPS) .build/pycheckreqs $(shell find anki aqt -name '*.py' | grep -v buildhash.py)

.build/mypy: $(PYCHECKDEPS)
	mypy anki aqt
	touch $@

.build/pytest: $(PYCHECKDEPS) $(wildcard tests/*.py)
	./tools/tests.sh
	touch $@

.build/pylint: $(PYCHECKDEPS)
	pylint -j 0 --rcfile=.pylintrc -f colorized --extension-pkg-whitelist=PyQt5 anki aqt
	touch $@

.build/pyimports: $(PYCHECKDEPS)
	isort anki aqt --check # if this fails, run 'make fixpyimports'
	touch $@

.build/pyfmt: $(PYCHECKDEPS)
	black --check $(BLACKARGS) # if this fails, run 'make fixpyfmt'
	touch $@

.PHONY: mypy pytest pylint pyimports pyfmt
mypy: .build/mypy
pytest: .build/pytest
pylint: .build/pylint
pyimports: .build/pyimports
pyfmt: .build/pyfmt

.PHONY: fixpyimports fixpyfmt

fixpyimports:
	isort anki aqt

fixpyfmt:
	black $(BLACKARGS) anki aqt

# Checking typescript
######################

TSCHECKDEPS := $(BUILDDEPS) $(TSDEPS)

.build/checkpretty: $(TSCHECKDEPS)
	(cd ts && npm run check-pretty) # if this fails, run 'make pretty'
	touch $@

.build/pretty: $(TSCHECKDEPS)
	(cd ts && npm run pretty)
	touch $@

.PHONY: pretty checkpretty
pretty: .build/pretty
checkpretty: .build/checkpretty
