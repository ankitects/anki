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

.PHONY: clean build run

clean:
	rm -rf .build
	rm -rf $(JSDEPS)

build: .build/ui js

.build/ui: $(shell find designer -name '*.ui')
	./tools/build_ui.sh
	touch $@

run: build
	./runanki ${RUNARGS}

CHECKDEPS := .build/ui $(shell find anki aqt -name '*.py')

.PHONY: check mypy test lint pytype

check: mypy test lint pytype
mypy: .build/mypy
test: .build/test
lint: .build/lint
pytype: .build/pytype

.build/mypy: $(CHECKDEPS)
	mypy anki aqt
	touch $@

.build/test: $(CHECKDEPS)
	./tools/tests.sh
	touch $@

.build/lint: $(CHECKDEPS)
	pylint -j 0 --rcfile=.pylintrc -f colorized --extension-pkg-whitelist=PyQt5 anki aqt
	touch $@

.build/pytype: $(CHECKDEPS)
	pytype --config pytype.conf
	touch $@

.PHONY: js

TSDEPS := $(wildcard ts/*.ts)
JSDEPS := $(patsubst ts/%.ts, web/%.js, $(TSDEPS))

js: $(JSDEPS)

web/%.js: ts/%.ts
	(cd ts && ./node_modules/.bin/tsc --lib es6,dom lib/global.d.ts $(notdir $<) --outFile ../web/$(notdir $@))
