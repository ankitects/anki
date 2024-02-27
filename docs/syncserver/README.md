# Building and running Anki sync server in Docker

This is an example Dockerfile contributed by an Anki user, which shows how you can run a self-hosted sync server,
similar to what AnkiWeb.net offers

Building and running the sync server within a container has the advantage of fully isolating
the build products and runtime dependencies from the rest of your system.

## Requirements

- [x] [Docker](https://docs.docker.com/get-started/)

# Building image

To proceed with build, you must specify `<tag>` in `ANKI_VERSION` by just running the below:

```bash
# Ensure you are running this command inside /docs/syncserver
docker build --no-cache --build-arg ANKI_VERSION=<tag> -t anki-sync-server .
```

# Run container

Once done with build, we can proceed with running this image with the following command:

```bash
# this will create anki server
docker run -d  -e "SYNC_USER1=admin:admin" -p 8080:8080 --name anki-sync-server anki-sync-server
```

However if you want to have multiple users, you must pass respective environment variable as follows `SYNC_USER1=test:test SYNC_USER2=test2:test2`

```bash
# this will create anki server with multiple users
docker run -d -e "SYNC_USER1=test:test" -e "SYNC_USER2=test2:test2" -p 8080:8080 --name anki-sync-server anki-sync-server
```

Moreover, you can pass additional env vars mentioned [here](https://docs.ankiweb.net/sync-server.html)
