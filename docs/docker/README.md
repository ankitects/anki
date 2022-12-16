# Building and running Anki in Docker

This is an example Dockerfile contributed by an Anki user, which shows how Anki
can be both built and run from within a container. It works by streaming the GUI
over an X11 socket.

Building and running Anki within a container has the advantage of fully isolating
the build products and runtime dependencies from the rest of your system, but it is
a somewhat niche approach, with some downsides such as an inability to display natively
on Wayland, and a lack of integration with desktop icons/filetypes. But even if you
do not use this Dockerfile as-is, you may find it useful as a reference.

Anki's Linux CI is also implemented with Docker, and the Dockerfiles for that may
also be useful for reference - they can be found in `.buildkite/linux/docker`.

# Build the Docker image

For best results, enable BuildKit (`export DOCKER_BUILDKIT=1`).

When in this current directory, one can build the Docker image like this:

```bash
docker build --tag anki --file Dockerfile ../../
```

When this is done, run `docker image ls` to see that the image has been created.

If one wants to build from the project's root directory, use this command:

```bash
docker build --tag anki --file docs/docker/Dockerfile .
```

# Run the Docker image

Anki starts a graphical user interface, and this requires some extra setup on the user's
end. These instructions were tested on Linux (Debian 11) and will have to be adapted for
other operating systems.

To allow the Docker container to pull up a graphical user interface, we need to run the
following:

```bash
xhost +local:root
```

Once done using Anki, undo this with

```bash
xhost -local:root
```

Then, we will construct our `docker run` command:

```bash
docker run --rm -it \
    --name anki \
    --volume $HOME/.local/share:$HOME/.local/share:rw \
    --volume /etc/passwd:/etc/passwd:ro \
    --user $(id -u):$(id -g) \
    --volume /tmp/.X11-unix:/tmp/.X11-unix:rw \
    --env DISPLAY=$DISPLAY \
    anki
```

Here is a breakdown of some of the arguments:

- Mount the current user's `~/.local/share` directory onto the container. Anki saves things
  into this directory, and if we don't mount it, we will lose any changes once the
  container exits. We mount this as read-write (`rw`) because we want to make changes here.

  ```bash
  --volume $HOME/.local/share:$HOME/.local/share:rw
  ```

- Mount `/etc/passwd` so we can enter the container as ourselves. We mount this as
  read-only because we definitely do not want to modify this.

  ```bash
  --volume /etc/passwd:/etc/passwd:ro
  ```

- Enter the container with our user ID and group ID, so we stay as ourselves.

  ```bash
  --user $(id -u):$(id -g)
  ```

- Mount the X11 directory that allows us to open displays.

  ```bash
  --volume /tmp/.X11-unix:/tmp/.X11-unix:rw
  ```

- Pass the `DISPLAY` variable to the container, so it knows where to display graphics.

  ```bash
  --env DISPLAY=$DISPLAY
  ```

# Running Dockerized Anki easily from the command line

One can create a shell function that executes the `docker run` command. Then one can
simply run `anki` on the command line, and Anki will open in Docker. Make sure to change
the image name to whatever you used when building Anki.

```bash
anki() {
    docker run --rm -it \
        --name anki \
        --volume $HOME/.local/share:$HOME/.local/share:rw \
        --volume /etc/passwd:/etc/passwd:ro \
        --user $(id -u):$(id -g) \
        --volume /tmp/.X11-unix:/tmp/.X11-unix:rw \
        --env DISPLAY=$DISPLAY \
        anki "$@"
}
```
