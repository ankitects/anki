#!/bin/bash

set -e

root=$(realpath $(dirname $0)/..)

# update currently used keys
./update-desktop-usage.sh head

# then remove unused keys
bazel run //rslib/i18n_helpers:garbage_collect_ftl_entries $root/ftl $root/ftl/usage
