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

# during bundle
../out/pyenv/bin/pip-compile $args $upgrade requirements.bundle.in 
../out/pyenv/bin/pip-compile $args $upgrade requirements.qt6_3.in
../out/pyenv/bin/pip-compile $args $upgrade requirements.qt6_4.in
# ../out/pyenv/bin/pip-compile $args $upgrade requirements.qt5_14.in
# ../out/pyenv/bin/pip-compile $args $upgrade requirements.qt5_15.in

# during build/development/testing
../out/pyenv/bin/pip-compile $args $upgrade requirements.dev.in
