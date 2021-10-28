#!/bin/bash

set -e

cd $(dirname $0)
ROOT=$(pwd)/../..
OUTPUT_ROOT=$ROOT/bazel-pkg
VENV=$OUTPUT_ROOT/venv
BAZEL_EXTERNAL=$(bazel info output_base --ui_event_filters=-INFO)/external

# ensure the wheels are built
(cd $ROOT && ./scripts/build)

# ensure venv exists
test -d $VENV || (
   mkdir -p $OUTPUT_ROOT
   (cd $ROOT && ./scripts/python -m venv $VENV)
)

# run the rest of the build in Python
. $ROOT/scripts/cargo-env
if [[ "$OSTYPE" == "darwin"* ]]; then
  if [ $(uname -m) != "arm64" ]; then
    bazel query @pyqt514//:* > /dev/null
  fi
else
  bazel query @pyqt515//:* > /dev/null
fi
$VENV/bin/python build.py $ROOT $BAZEL_EXTERNAL
