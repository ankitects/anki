#!/bin/bash
#
# Anki bundles sip 5 generated typings that allow type checking Qt code when
# installed next to the original modules. Attempting to use them as a separate
# stubs distribution with MYPYPATH yielded a bunch of errors which I was not
# able to resolve. A solution that doesn't require modifying the python install
# would be welcome!

set -eo pipefail

TOOLS="$(cd "`dirname "$0"`"; pwd)"
modDir=$(python -c 'import PyQt5, sys, os; sys.stdout.write(os.path.dirname(sys.modules["PyQt5"].__file__))')

unameOut="$(uname -s)"
case "${unameOut}" in
    CYGWIN*)
        modDir="$(cygpath -u "${modDir}")"
        ;;
esac

if [[ "w${OS}" == "wWindows_NT" ]];
then
    rsync -a "${TOOLS}/stubs/PyQt5/" "${modDir}/"
else
    rsync -a "${TOOLS}/stubs/PyQt5/" "${modDir}/" || sudo rsync -a "${TOOLS}/stubs/PyQt5/" "${modDir}/"
fi
