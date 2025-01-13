#!/bin/bash
# - use './run.sh' to run in the foreground
# - use './run.sh serve' to daemonize.

set -e

. common.inc

if [ "$1" = "serve" ]; then
    extra_args="-d --restart always"
else
    extra_args="-it"
fi

name=anki-${platform}

# Stop and remove the existing container if it exists.
# This doesn't delete the associated volume.
if docker container inspect $name > /dev/null 2>&1; then
    docker stop $name || true
    docker container rm $name
fi

docker run $extra_args \
    --name $name \
    -v ${name}-state:/state \
    -e BUILDKITE_AGENT_TOKEN \
    -e BUILDKITE_AGENT_TAGS \
    $name
