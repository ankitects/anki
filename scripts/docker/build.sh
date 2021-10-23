#!/bin/bash

set -e

test -e WORKSPACE || (
    echo "Run from project root"
    exit 1
)

arch=$1

if [ "$arch" != "amd64" -a "$arch" != "arm64" ]; then
    echo "usage: build [amd64|arm64]"
    exit 1
fi

rm -rf bazel-dist

export DOCKER_BUILDKIT=1

docker build --tag ankibuild --file scripts/docker/Dockerfile.$arch \
    --build-arg uid=$(id -u) --build-arg gid=$(id -g) \
    scripts/docker
docker run --rm -it \
    --mount type=bind,source="$(pwd)",target=/code \
    ankibuild
