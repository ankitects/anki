#!/bin/bash

set -e

version=$1
root=$(realpath $(dirname $0)/..)

bazel run //rslib/i18n_helpers:write_ftl_json $root/ftl/usage/desktop-$version.json \
    $root/{rslib,ts,pylib,qt}
