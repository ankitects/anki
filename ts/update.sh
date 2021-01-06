#!/bin/bash
# Update JS dependencies and dump runtime licenses to licenses.json

set -e

bazel run yarn upgrade
./update-licenses.sh