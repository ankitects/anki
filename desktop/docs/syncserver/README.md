# Building and running Anki sync server in Docker

This is an example Dockerfile contributed by an Anki user, which shows how you can run a self-hosted sync server,
similar to what AnkiWeb.net offers.

Building and running the sync server within a container has the advantage of fully isolating
the build products and runtime dependencies from the rest of your system.

## Requirements

- [x] [Docker](https://docs.docker.com/get-started/)

| **Aspect**             | **Dockerfile**                                             | **Dockerfile.distroless**                                 |
| ---------------------- | ---------------------------------------------------------- | --------------------------------------------------------- |
| **Shell & Tools**      | ✅ Includes shell and tools                                | ❌ Minimal, no shell or tools                             |
| **Debugging**          | ✅ Easier debugging with shell and tools                   | ❌ Harder to debug due to minimal environment             |
| **Health Checks**      | ✅ Supports complex health checks                          | ❌ Health checks need to be simple or directly executable |
| **Image Size**         | ❌ Larger image size                                       | ✅ Smaller image size                                     |
| **Customization**      | ✅ Easier to customize with additional packages            | ❌ Limited customization options                          |
| **Attack Surface**     | ❌ Larger attack surface due to more installed packages    | ✅ Reduced attack surface                                 |
| **Libraries**          | ✅ More libraries available                                | ❌ Limited libraries                                      |
| **Start-up Time**      | ❌ Slower start-up time due to larger image size           | ✅ Faster start-up time                                   |
| **Tool Compatibility** | ✅ Compatible with more tools and libraries                | ❌ Compatibility limitations with certain tools           |
| **Maintenance**        | ❌ Higher maintenance due to larger image and dependencies | ✅ Lower maintenance with minimal base image              |
| **Custom uid/gid**     | ✅ It's possible to pass in PUID and PGID                  | ❌ PUID and PGID are not supported                        |

# Building image

To proceed with building, you must specify the Anki version you want, by replacing `<version>` with something like `24.11` and `<Dockerfile>` with the chosen Dockerfile (e.g., `Dockerfile` or `Dockerfile.distroless`)

The `--platform` option specifies which systems the image will be able to run on after it has been built. This is helpful in scenarios where you are building the image on one architecture (eg: an M series Mac) but running the image on another architecture (eg: x86 Linux server).

- `linux/amd64` is for x86 linux systems (Images built with this option will work on Windows as well through WSL2 which is included with Docker Desktop)
- `linux/arm64` is for ARM systems (eg: M series Macs)

On Linux systems, you will have to configure a custom builder which supports ARM builds first as shown below. You can then run the Mac build command in the next step.

```bash
# Configuring multiplatform builder and activating it
docker buildx create --name multiplatform-builder --driver docker-container --driver-opt default-load=true --bootstrap --use
```

On M-series Macs running Docker Desktop, you can build for both platforms using the default builder by running the command below:

```bash
# Execute this command from this directory
docker buildx build -f <Dockerfile> --platform linux/amd64,linux/arm64 --no-cache --build-arg ANKI_VERSION=<version> -t anki-sync-server .
```

If the build fails with `Resource Exhausted:.... cannot allocate memory`, go to Docker Desktop > Settings > Resources and increase the memory limit.

If you are unsure of what platform your system is, just run the command below without the platform option.

```bash
# Execute this command from this directory
docker buildx build -f <Dockerfile> --no-cache --build-arg ANKI_VERSION=<version> -t anki-sync-server .
```

# Run container

Once done with build, you can proceed with running this image with the following command:

```bash
# this will create anki server
docker run -d \
    -e "SYNC_USER1=admin:admin" \
    -p 8080:8080 \
    --mount type=volume,src=anki-sync-server-data,dst=/anki_data \
    --name anki-sync-server \
    anki-sync-server
```

If the image you are using was built with `Dockerfile` you can specify the
`PUID` and `PGID` env variables for the user and group id of the process that
will run the anki-sync-server process. This is valuable when you want the files
written and read from the `/anki_data` volume to belong to a particular
user/group e.g. to access it from the host or another container. Note the the
ids chosen for `PUID` and `PGID` must not already be in use inside the
container (1000 and above is fine). For example add `-e "PUID=1050"` and `-e
"PGID=1050"` to the above command.

If you want to have multiple Anki users that can sync their devices, you can
specify multiple `SYNC_USER` as follows:

```bash
# this will create anki server with multiple users
docker run -d \
    -e "SYNC_USER1=admin:admin" \
    -e "SYNC_USER2=admin2:admin2" \
    -p 8080:8080 \
    --mount type=volume,src=anki-sync-server-data,dst=/anki_data \
    --name anki-sync-server \
    anki-sync-server
```

Moreover, you can pass additional env vars mentioned
[here](https://docs.ankiweb.net/sync-server.html). Note that `SYNC_BASE` and
`SYNC_PORT` will be ignored. In the first case for safety reasons, to avoid
accidentally placing data outside the volume and the second for simplicity
since the internal port of the container does not matter given that you can
change the external one.

# Upgrading

If your image was built after January 2025 then you can just build a new image
and start a new container with the same configuration as the previous
container. Everything should work as expected.

If the image you were running was built **before January 2025** then it did not
contain a volume, meaning all syncserver data was stored inside the container.
If you discard the container, for example because you want to build a new
container using an updated image, then your syncserver data will be lost.

The easiest way of working around this is by ensuring at least one of your
devices is fully in sync with your syncserver before upgrading the Docker
container. Then after upgrading the container when you try to sync your device
it will tell you that the server has no data. You will then be given the option
of uploading all local data from the device to syncserver.
