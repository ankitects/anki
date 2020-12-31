#!/bin/bash
# Dump runtime licenses to licenses.json

set -e

./node_modules/.bin/license-checker-rseidelsohn --production --json \
    --excludePackages anki --relativeLicensePath \
    --relativeModulePath > licenses.json
