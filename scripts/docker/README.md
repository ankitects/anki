# Building in Docker

This folder contains a script for building Anki inside a Docker container.
It works by creating an image with the required dependencies, and then runs the
build with the source folder mounted into the image. This will cause files to be
written into `bazel-\*` and `node_modules` in the source folder as the build proceeds.
The advantage of doing it this way is that most of the efficiency of building
outside Docker is retained - you can make minor changes and run the build again,
and only the changed parts need to be rebuilt.

If you're looking for a fully isolated build, [this other
approach](../../docs/docker/README.md) in the docs folder may suit you better. As
it also includes runtime dependencies, it may be a useful reference for libraries
you'll need to install before running Anki.

# Usage

Ensure Docker is installed on your machine, and your user has permission to connect
to Docker. Then run the following command from the root folder of this source repo:

```
$ scripts/docker/build.sh amd64
```

The resulting wheels will be written into bazel-dist. See
[Development](../docs/development.md) for information on how to install them.

If you're on an ARM Linux machine, replace amd64 with arm64.
