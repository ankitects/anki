#!/bin/bash
# Execute subcommand (eg 'yarn <cmd> ...') and update licenses.

set -e

bazel run yarn -- $*
./update-licenses.sh
