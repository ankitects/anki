#!/bin/bash
# builds an 'anki-[amd|arm]' image for the current platform
#
# for a cross-compile on recent Docker:
#   docker buildx create --use
#   docker run --privileged --rm tonistiigi/binfmt --install amd64
#   docker buildx build --platform linux/amd64 --tag anki-amd64 . --load

. common.inc

DOCKER_BUILDKIT=1 docker build --tag anki-${platform} .
