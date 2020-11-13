# Needs updating

The following was written before the build system changed, and will
need modifications to work.

# Anki in Docker

Anki can optionally be built and run with Docker, which will automate the
installation of Anki's build dependencies. The instructions below cover running
Docker on Linux; to run it on other platforms you will need to run an X server
and adapt the instructions.

For information on building Anki outside of Docker, please see README.development.

## Running Anki in Docker

Build and then run the image. The `docker run` command below runs the image as the
current user, and it mounts the user's `$HOME` directory, which is where Anki stores
its local files.

```
docker build --tag anki .
xhost +local:root  # Undo when done with `xhost -local:root`
docker run \
    --rm -it \
    --user 1000:1000 \
    --volume $HOME/.local/share:$HOME/.local/share:rw \
    --volume /etc/passwd:/etc/passwd:ro \
    --volume /tmp/.X11-unix:/tmp/.X11-unix:rw \
    --env DISPLAY=$DISPLAY \
    anki
xhost -local:root
```

## Developing Anki in Docker

Build your local source tree in Docker.

1.  Build the Docker image with build-time dependencies. The Anki Dockerfile uses
    multi-stage builds, so the target is the first stage, which includes only the
    dependencies.

        ```
        docker build --tag anki:dependencies --target dependencies .
        ```

2.  Compile your source tree

    Start the image with dependencies in the background. It is important to run the
    image as the current user, because otherwise, some files in the source tree will be
    owned by root. Find user id with `id -u` and group ID with `id -g`. These values
    are passed to `--user` as in `--user $(id -u):$(id -g)`.

    ```
    docker run --rm -it \
        --name ankibuilder \
        --detach \
        --workdir /work
        --volume "$PWD":/work:rw \
        --user 1000:1000 \
        --volume /etc/passwd:/etc/passwd:ro \
        --volume /tmp/.X11-unix:/tmp/.X11-unix:rw \
        --env DISPLAY=$DISPLAY \
        anki:dependencies bash
    ```

    Allow the Docker container to use the local X server and show the GUI.

    ```
    xhost +local:root
    ```

    (Undo this when done with `xhost -local:root`)

    Compile.

    ```
    docker exec -it ankibuilder make run
    ```

    The Anki graphical user interface should appear. The first run will take some time
    because Rust code has to be compiled and Python dependencies have to be downloaded,
    etc. The following runs will be much faster.

    To compile without running the GUI, use `make develop`.

3.  Other common operations

    If system packages need to be installed, use `apt-get` as below. The Docker image
    is based on a Debian Stable image.

    ```
    docker exec -it --user root ankibuilder apt-get update
    docker exec -it --user root ankibuilder apt-get install PACKAGES
    ```

    An interactive bash shell can be started with

    ```
    docker exec -it ankibuilder bash
    ```

    or as root user

    ```
    docker exec -it --user root ankibuilder bash
    ```

Old docker file below:

```
ARG PYTHON_VERSION="3.8"

FROM python:$PYTHON_VERSION AS dependencies

# Allow non-root users to install things and modify installations in /opt.
RUN chmod 777 /opt && chmod a+s /opt

# Install rust.
ENV CARGO_HOME="/opt/cargo" \
    RUSTUP_HOME="/opt/rustup"
ENV PATH="$CARGO_HOME/bin:$PATH"
RUN mkdir $CARGO_HOME $RUSTUP_HOME \
    && chmod a+rws $CARGO_HOME $RUSTUP_HOME \
    && curl -fsSL --proto '=https' --tlsv1.2 https://sh.rustup.rs \
    | sh -s -- -y --quiet --no-modify-path \
    && rustup update \
    && cargo install ripgrep

# Install system dependencies.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
    gettext \
    lame \
    libnss3 \
    libxcb-icccm4 \
    libxcb-image0 \
    libxcb-keysyms1 \
    libxcb-randr0 \
    libxcb-render-util0 \
    libxcb-xinerama0 \
    libxcb-xkb1 \
    libxkbcommon-x11-0 \
    libxcomposite1 \
    mpv \
    portaudio19-dev \
    rsync \
    && rm -rf /var/lib/apt/lists/*

# Install node and npm.
WORKDIR /opt/node
RUN curl -fsSL --proto '=https' https://nodejs.org/dist/v12.18.3/node-v12.18.3-linux-x64.tar.xz \
    | tar xJ --strip-components 1
ENV PATH="/opt/node/bin:$PATH"

# Install protoc.
WORKDIR /opt/protoc
RUN curl -fsSL --proto '=https' -O https://github.com/protocolbuffers/protobuf/releases/download/v3.11.4/protoc-3.11.4-linux-x86_64.zip \
    && unzip protoc-3.11.4-linux-x86_64.zip -x readme.txt \
    && rm protoc-3.11.4-linux-x86_64.zip
ENV PATH="/opt/protoc/bin:$PATH"

# Allow non-root users to install toolchains and update rust crates.
RUN chmod 777 $RUSTUP_HOME/toolchains $RUSTUP_HOME/update-hashes $CARGO_HOME/registry \
    && chmod -R a+rw $CARGO_HOME/registry \
    # Necessary for TypeScript.
    && chmod a+w /home

# Build anki. Use a separate image so users can build an image with build-time
# dependencies.
FROM dependencies AS builder
WORKDIR /opt/anki
COPY . .
RUN make develop

FROM builder AS pythonbuilder
RUN make build

# Build final image.
FROM python:${PYTHON_VERSION}-slim

# Install system dependencies.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
    gettext \
    lame \
    libnss3 \
    libxcb-icccm4 \
    libxcb-image0 \
    libxcb-keysyms1 \
    libxcb-randr0 \
    libxcb-render-util0 \
    libxcb-xinerama0 \
    libxcb-xkb1 \
    libxkbcommon-x11-0 \
    libxcomposite1 \
    mpv \
    portaudio19-dev \
    rsync \
    && rm -rf /var/lib/apt/lists/*

# Install pre-compiled Anki.
COPY --from=pythonbuilder /opt/anki/dist/ /opt/anki/
RUN python -m pip install --no-cache-dir \
    PyQtWebEngine \
    /opt/anki/*.whl \
    # Create an anki executable.
    && printf "#!/usr/bin/env python\nimport aqt\naqt.run()\n" > /usr/local/bin/anki \
    && chmod +x /usr/local/bin/anki \
    # Create non-root user.
    && useradd --create-home anki

USER anki

ENTRYPOINT ["/usr/local/bin/anki"]

LABEL maintainer="Jakub Kaczmarzyk <jakub.kaczmarzyk@gmail.com>"
```
