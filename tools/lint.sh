#!/bin/bash

TOOLS="$(cd "`dirname "$0"`"; pwd)"
pylint --rcfile=$TOOLS/../.pylintrc $TOOLS/../anki $TOOLS/../aqt
