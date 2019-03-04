#!/bin/bash

TOOLS="$(cd "`dirname "$0"`"; pwd)"
pylint -E -j 0 -f colorized $TOOLS/../anki $TOOLS/../aqt
