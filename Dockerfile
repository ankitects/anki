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
