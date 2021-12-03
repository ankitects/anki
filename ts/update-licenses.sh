#!/bin/bash
# Dump runtime licenses to licenses.json

set -e

cd .. && bazel run ts:node -- ./node_modules/.bin/license-checker-rseidelsohn --production --json \
    --excludePackages anki --relativeLicensePath \
    --relativeModulePath > ts/licenses.json
