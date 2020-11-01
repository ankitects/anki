#!/bin/bash

echo "STABLE_VERSION 2.1.36"
if [ "$ANKI_RELEASE" != "" ]; then
    echo "STABLE_BUILDHASH $(git rev-parse --short=8 HEAD || echo nogit)"
else
    echo "STABLE_BUILDHASH dev"
fi
