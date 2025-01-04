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

# Building image

To proceed with building, you must specify the Anki version you want, by replacing `<version>` with something like `24.11` and `<Dockerfile>` with the chosen Dockerfile (e.g., `Dockerfile` or `Dockerfile.distroless`)

```bash
# Execute this command from the root directory of your project
docker build -f docs/syncserver/<Dockerfile> --no-cache --build-arg ANKI_VERSION=<version> -t anki-sync-server .
```

# Run container

Once done with build, you can proceed with running this image with the following command:

```bash
# this will create anki server
docker run -d  -e "SYNC_USER1=admin:admin" -p 8080:8080 --name anki-sync-server anki-sync-server
```

However, if you want to have multiple users, you have to use the following approach:

```bash
# this will create anki server with multiple users
docker run -d -e "SYNC_USER1=test:test" -e "SYNC_USER2=test2:test2" -p 8080:8080 --name anki-sync-server anki-sync-server
```

Moreover, you can pass additional env vars mentioned [here](https://docs.ankiweb.net/sync-server.html)
