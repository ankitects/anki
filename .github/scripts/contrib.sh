#!/bin/bash

set -e

echo "All contributors:"
git log --pretty=format:' - %ae' CONTRIBUTORS |sort |uniq |sort -f

headAuthor=$(git log -1 --pretty=format:'%ae')
if git log --pretty=format:'%ae' CONTRIBUTORS | grep -q "$headAuthor"; then
    echo "Author $headAuthor found in CONTRIBUTORS"
else
    echo "Author $headAuthor NOT found in list"
    exit 1
fi
