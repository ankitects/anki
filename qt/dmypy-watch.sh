#!/bin/bash
#
# semi-working support for mypy daemon
# - install fs_watch
# - build anki/aqt wheels first
# - create a new venv and activate it
# - install the wheels
# - then run this script from this folder

(sleep 1 && touch aqt)
. ~/pyenv/bin/activate
fswatch -o aqt | xargs -n1 -I{} sh -c 'printf \\033c\\n; dmypy run aqt'

