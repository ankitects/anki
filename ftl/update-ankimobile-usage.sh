#!/bin/bash
#
# This script can only be run by Damien, as it requires a copy of AnkiMobile's sources.
# A similar script could be added for AnkiDroid in the future.
#

set -e

scriptRoot=$(realpath $(dirname $0)/..)
sourceRoot=$(realpath $scriptRoot/../mob/src)

bazel run //rslib/i18n_helpers:write_ftl_json $scriptRoot/ftl/usage/ankimobile.json \
    $sourceRoot
