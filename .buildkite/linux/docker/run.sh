#!/bin/bash
# - use 'BUILD=1 ./run.sh' to build image & run.
# - use './run.sh serve' to daemonize.

set -e

if [ "$1" = "serve" ]; then
    extra_args="-d --restart always"
else
    extra_args="-it"
fi

if [ $(uname -m) = "aarch64" ]; then
    arch=arm64
else
    arch=amd64
fi

if [ -n "$BUILD" ]; then
    DOCKER_BUILDKIT=1 docker build -f Dockerfile.${arch} --tag linci .
fi

if docker container inspect linci > /dev/null 2>&1; then
    docker stop linci || true
    docker container rm linci
fi

docker run $extra_args \
    --name linci \
    -v ci-state:/state \
    -e BUILDKITE_AGENT_TOKEN \
    -e BUILDKITE_AGENT_TAGS \
    linci
