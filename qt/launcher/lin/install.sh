#!/bin/bash

set -e

if [ "$(dirname "$(realpath "$0")")" != "$(realpath "$PWD")" ]; then
  echo "Please run from the folder install.sh is in."
  exit 1
fi

MINIMUM_REQUIRED_GLIBC_VERSION=2.36
# `ldd --version` returns the version of glibc, such as 2.41 with other text.
# `grep --only-matching "[0-9]\+.[0-9]\+"` returns the (version) numbers.
# `head --lines=1` only returns the first number (which is the glibc version)
users_glibc_version=$(
    ldd --version | grep --only-matching "[0-9]\+.[0-9]\+" | head --lines=1
)

# check if the users glibc is less than the required glibc and abort if true.
# bash doesn't do floating point comparisons. But integer comparisons do work.
# Thus, `$(echo $variable | sed "s/\.//")` is used to get an integer
# representation of the floating point number (2.36 becomes 236).
if [ $(echo $users_glibc_version | sed "s/\.//") -lt \
     $(echo $MINIMUM_REQUIRED_GLIBC_VERSION | sed "s/\.//") ]; then
    echo "Error: Your glibc version is $users_glibc_version but" \
         "$MINIMUM_REQUIRED_GLIBC_VERSION is required. Aborting."
    exit 1
fi

if [ "$PREFIX" = "" ]; then
	PREFIX=/usr/local
fi

rm -rf "$PREFIX"/share/anki "$PREFIX"/bin/anki
mkdir -p "$PREFIX"/share/anki
cp -av --no-preserve=owner,context -- * .python-version "$PREFIX"/share/anki/
mkdir -p "$PREFIX"/bin
ln -sf "$PREFIX"/share/anki/anki "$PREFIX"/bin/anki
# fix a previous packaging issue where we created this as a file
(test -f "$PREFIX"/share/applications && rm "$PREFIX"/share/applications)||true
mkdir -p "$PREFIX"/share/pixmaps
mkdir -p "$PREFIX"/share/applications
mkdir -p "$PREFIX"/share/man/man1
cd "$PREFIX"/share/anki && (\
mv -Z anki.xpm anki.png "$PREFIX"/share/pixmaps/;\
mv -Z anki.desktop "$PREFIX"/share/applications/;\
mv -Z anki.1 "$PREFIX"/share/man/man1/)

xdg-mime install anki.xml --novendor
xdg-mime default anki.desktop application/x-colpkg
xdg-mime default anki.desktop application/x-apkg
xdg-mime default anki.desktop application/x-ankiaddon

rm install.sh

echo "Install complete. Type 'anki' to run."
