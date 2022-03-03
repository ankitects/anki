#!/bin/bash

echo "STABLE_BUILDHASH $(git rev-parse --short=8 HEAD || echo nogit)"
