#!/bin/bash

TOOLS="$(cd "`dirname "$0"`"; pwd)"
mypy $TOOLS/../anki $TOOLS/../aqt
