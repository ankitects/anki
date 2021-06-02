#!/bin/bash

set -e

if ! test -f install.sh; then
  echo "Please run from the folder install.sh is in."
  exit 1
fi

if [ "$PREFIX" = "" ]; then
	PREFIX=/usr/local
fi

rm -rf ${PREFIX}/share/anki ${PREFIX}/bin/anki
mkdir -p ${PREFIX}/share/anki
cp -av * ${PREFIX}/share/anki/
mkdir -p ${PREFIX}/bin
ln -sf ${PREFIX}/share/anki/bin/Anki ${PREFIX}/bin/anki
# fix a previous packaging issue where we created this as a file
(test -f ${PREFIX}/share/applications && rm ${PREFIX}/share/applications)||true
mkdir -p ${PREFIX}/share/pixmaps
mkdir -p ${PREFIX}/share/applications
mkdir -p ${PREFIX}/share/man/man1
cd ${PREFIX}/share/anki && (\
mv anki.xpm anki.png ${PREFIX}/share/pixmaps/;\
mv anki.desktop ${PREFIX}/share/applications/;\
mv anki.1 ${PREFIX}/share/man/man1/)

xdg-mime install anki.xml --novendor
xdg-mime default anki.desktop application/x-colpkg
xdg-mime default anki.desktop application/x-apkg
xdg-mime default anki.desktop application/x-ankiaddon

echo "Install complete. Type 'anki' to run."
