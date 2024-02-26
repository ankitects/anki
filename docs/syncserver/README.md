# Building and running Anki sync server in Docker

This is an example Dockerfile contributed by an Anki user, which shows how we can run Self-Hosted Sync Server
similar to what AnkiWeb.net offers

Building and running Anki Sync Server within a container has the advantage of fully isolating
the build products and runtime dependencies from the rest of your system.

## Requirements

- [x] [Docker](https://docs.docker.com/get-started/)

# Building image

By default docker buildx use the following version `ANKI_VERSION=23.2.1` by just running the below you should be able to build your own image:

```bash
# Ensure you are running this command inside /docs/server
docker build --no-cache -t anki-sync-server .
# Alternatively, you can specify version of your choice
docker build --no-cache --build-arg ANKI_VERSION=<tag> -t anki-sync-server .
```

# Run container

Once done with build, we can proceed with running this image with the following command:

```bash
# this will create anki server with default variables
# SYNC_USER1:admin:admin and you can bind host port of your choice
docker run -d -p 8080:8080 --name anki-sync-server anki-sync-server
```

However if you want to have multiple users, you must pass environment variable `SYNC_USERS` as follows `SYNC_USERS=user:user user2:user2 user3:user3 usern:usern` separated by space.

```bash
docker run -d -e "SYNC_USERS=user:user user2:user2 user3:user3" -p 8080:8080 --name anki-sync-server anki-sync-server
```

Moreover, you can pass additional env vars mentioned [here](https://docs.ankiweb.net/sync-server.html)
