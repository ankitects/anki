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

```bash
# Execute this command from this directory
docker build -f <Dockerfile> --no-cache --build-arg ANKI_VERSION=<version> -t anki-sync-server .
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
[here](https://docs.ankiweb.net/sync-server.html). Note that you should **not**
override SYNC_BASE because you risk data loss.
