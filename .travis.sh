#!/bin/bash

set -e

echo "running unit tests..."
nosetests ./tests

echo "building ui..."
./tools/build_ui.sh

echo "linting..."
./tools/lint.sh
