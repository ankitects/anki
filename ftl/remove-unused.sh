#!/bin/bash
#
# To use, run:
#
# - ./update-ankimobile-usage.sh
# - ./remove-unused.sh
#
# If you need to maintain compatibility with an older stable branch, you
# can use ./update-desktop-usage.sh in the older release, then copy the
# generated file into usage/ with a different name.
#   
# Caveats:
#   - Messages are considered in use if they are referenced in other messages,
#     even if those messages themselves are not in use and going to be deleted.
#   - Usually, if there is a bug and a message is failed to be recognised as in
#     use, building will fail. However, this is not true for nested message, for
#     which only a runtime error will be printed.

set -e

root=$(realpath $(dirname $0)/..)

# update currently used keys
./update-desktop-usage.sh head

# then remove unused keys
bazel run //rslib/i18n_helpers:garbage_collect_ftl_entries $root/ftl $root/ftl/usage
