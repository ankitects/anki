#!/bin/bash

set -e

if [ "$1" == "all" ]; then
    upgrade="--upgrade"
elif [ "$1" != "" ]; then
    upgrade="--upgrade-package $1"
else
    upgrade=""
fi

args="--resolver=backtracking --allow-unsafe --no-header --strip-extras --generate-hashes"

# initial pyenv bootstrap
../out/pyenv/bin/pip-compile $args $upgrade requirements.base.in

# during build/development/testing
../out/pyenv/bin/pip-compile $args $upgrade requirements.dev.in

# during bundle
../out/pyenv/bin/pip-compile $args $upgrade requirements.bundle.in 
../out/pyenv/bin/pip-compile $args $upgrade requirements.qt6_win.in
../out/pyenv/bin/pip-compile $args $upgrade requirements.qt6_lin.in
../out/pyenv/bin/pip-compile $args $upgrade requirements.qt6_mac.in

