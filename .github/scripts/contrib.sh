#!/bin/bash

set -e

headAuthor=$(git log -1 --pretty=format:'%ae')
git log --pretty=format:'%ae' CONTRIBUTORS | grep -q "$headAuthor"

echo "$headAuthor found in CONTRIBUTORS"
