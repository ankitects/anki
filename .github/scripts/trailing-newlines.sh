#!/bin/bash

set -e

files=$(rg -l '[^\n]\z' -g '!*.{svg,scss}' || true)
if [ "$files" != "" ]; then
    echo "the following files are missing a newline on the last line:"
    echo $files
    exit 1
fi
