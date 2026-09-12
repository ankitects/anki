#!/bin/bash

set -e

if [ "$PREFIX" = "" ]; then
	PREFIX=/usr/local
fi

echo "Uninstalling Anki..."
xdg-mime uninstall "$PREFIX"/share/anki/anki.xml || true

rm -rf "$PREFIX"/share/anki
rm -rf "$PREFIX"/bin/anki
rm -rf "$PREFIX"/share/pixmaps/anki.xpm
rm -rf "$PREFIX"/share/pixmaps/anki.png
rm -rf "$PREFIX"/share/applications/anki.desktop
rm -rf "$PREFIX"/share/man/man1/anki.1

echo "Uninstall complete."
