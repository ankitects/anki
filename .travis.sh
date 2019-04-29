#!/bin/bash

set -e

echo "building ui..."
./tools/build_ui.sh

echo "running unit tests..."
nosetests ./tests

echo "linting..."
./tools/lint.sh
