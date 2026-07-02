#!/bin/bash
# Walk backwards from HEAD, testing each commit until the
# provided first commit (inclusive).

set -e

if [ "$1" = "" ]; then
    echo "usage: test-commits.sh [first-commit-hash]"
    exit 1
fi

first_commit="$1"

while :; do
    echo "testing $(git log --pretty=oneline -1)"
    ./gradlew -q clean uninstallPlayDebug jacocoTestReport
    ./gradlew -q :api:lintRelease :AnkiDroid:lintPlayRelease ktlintCheck
    ./gradlew --stop
    [ $(git rev-parse HEAD) = $first_commit ] && break
    git checkout HEAD^
done
