#!/bin/bash

set -eu -o pipefail ${SHELLFLAGS}

# Checking version to force it fail the build if rg is not installed.
# Because `set -e` does not work inside the subshell $()
if ! rg --version > /dev/null 2>&1;
then
    echo "Error: ripgrep is not installed!";
    exit 1;
fi;

files=$(rg -l '[^\n]\z' -g '!*.{png,svg,scss,json,sql}' || true)

if [ "$files" != "" ]; then
    echo "the following files are missing a newline on the last line:"
    echo $files
    exit 1
fi
