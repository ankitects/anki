#!/bin/bash
# Execute subcommand (eg 'yarn <cmd> ...') and update licenses.

set -e

PATH=$(realpath ../out/extracted/node/bin)

../out/extracted/node/bin/yarn $*

cd ..

./node_modules/.bin/license-checker-rseidelsohn --production --json \
    --excludePackages anki --relativeLicensePath \
    --relativeModulePath > ts/licenses.json
