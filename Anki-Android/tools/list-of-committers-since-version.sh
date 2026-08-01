#!/bin/bash
# List of committers since a particular commit or tag
# Usage: ./list-of-committers-since-version.sh v0.7

TAG=$1

git log $TAG.. --format="- %aN" --reverse | perl -e 'my %dedupe; while (<STDIN>) { print unless $dedupe{$_}++}'
