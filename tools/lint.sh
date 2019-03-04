#!/bin/bash

TOOLS="$(cd "`dirname "$0"`"; pwd)"
pylint -E -j 0 -f colorized --disable=E0602 $TOOLS/../anki $TOOLS/../aqt
