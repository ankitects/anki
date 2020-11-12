#!/bin/bash
# Update JS dependencies and dump runtime licenses to licenses.json

set -e

bazel run @nodejs//:yarn upgrade
./node_modules/.bin/license-checker --production --json --excludePackages anki > licenses.json
