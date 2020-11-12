#!/bin/bash
# Update JS dependencies and dump runtime licenses to licenses.json

set -e

bazel run @nodejs//:yarn upgrade
./node_modules/.bin/license-checker-rseidelsohn --production --json \
    --excludePackages anki --relativeLicensePath \
    --relativeModulePath > licenses.json
