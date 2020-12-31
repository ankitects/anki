#!/bin/bash
# Add a dependency (eg 'yarn add ...') and update licenses.

set -e

bazel run yarn -- add $*
./update-licenses.sh
