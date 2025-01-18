#!/bin/bash

set -e

if [[ "$(uname -m)" == "x86_64" ]]; then
    platform="amd"
else
    platform="arm"
fi
