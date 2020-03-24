#!/bin/bash

set -eo pipefail

# Checking version to force it fail the build if rg is not installed.
# Because `set -e` does not work inside the subshell $()
rg --version > /dev/null 2>&1

files=$(rg -l '[^\n]\z' -g '!*.{png,svg,scss,json,sql}' || true)

if [ "$files" != "" ]; then
    echo "the following files are missing a newline on the last line:"
    echo $files
    exit 1
fi
