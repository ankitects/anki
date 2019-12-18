PREFIX := /usr
SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.ONESHELL:
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules
RUNARGS :=
.SUFFIXES:

$(shell mkdir -p .build)

# Installing
######################

.PHONY: all install uninstall

all:
	@echo "You can run Anki with ./runanki"
	@echo "If you wish to install it system wide, type 'sudo make install'"
	@echo "Uninstall with 'sudo make uninstall'"

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

REQS := .build/pyrunreqs .build/pydevreqs .build/jsreqs

.build/pyrunreqs: requirements.txt
	pip install -r $<
	touch $@

.build/pydevreqs: requirements.dev
	pip install -r $<
	touch $@

.build/jsreqs: ts/package.json
	(cd ts && npm i)
	touch $@

# Building
######################

BUILDDEPS := $(REQS) .build/ui .build/js

.build/ui: $(shell find designer -name '*.ui')
	./tools/build_ui.sh
	touch $@

.build/js: $(TSDEPS)
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
check: mypy pytest pylint pytype checkpretty

# Checking python
######################

PYCHECKDEPS := $(BUILDDEPS) $(shell find anki aqt -name '*.py')

.build/mypy: $(PYCHECKDEPS)
	mypy anki aqt
	touch $@

.build/pytest: $(PYCHECKDEPS)
	./tools/tests.sh
	touch $@

.build/pylint: $(PYCHECKDEPS)
	pylint -j 0 --rcfile=.pylintrc -f colorized --extension-pkg-whitelist=PyQt5 anki aqt
	touch $@

.build/pytype: $(PYCHECKDEPS)
	pytype --config pytype.conf
	touch $@

.PHONY: mypy pytest pylint pytype
mypy: .build/mypy
pytest: .build/pytest
pylint: .build/pylint
pytype: .build/pytype

# Typescript source
######################

TSDEPS := $(wildcard ts/src/*.ts)
JSDEPS := $(patsubst ts/src/%.ts, web/%.js, $(TSDEPS))

# Checking typescript
######################

TSCHECKDEPS := $(BUILDDEPS) $(TSDEPS)

.build/checkpretty: $(TSCHECKDEPS)
	(cd ts && npm run check-pretty)
	touch $@

.build/pretty: $(TSCHECKDEPS)
	(cd ts && npm run pretty)
	touch $@

.PHONY: pretty checkpretty
pretty: .build/pretty
checkpretty: .build/checkpretty
