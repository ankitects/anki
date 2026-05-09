# This is a user-contributed Dockerfile. No official support is available.

ARG DEBIAN_FRONTEND="noninteractive"

FROM ubuntu:24.04 AS build
WORKDIR /opt/anki
ENV PYTHON_VERSION="3.13"


# System deps
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    git \
    build-essential \
    pkg-config \
    libssl-dev \
    libbz2-dev \
    libreadline-dev \
    libsqlite3-dev \
    libffi-dev \
    zlib1g-dev \
    liblzma-dev \
    ca-certificates \
    ninja-build \
    rsync \
    libglib2.0-0 \
    libgl1 \
    libx11-6 \
    libxext6 \
    libxrender1 \
    libxkbcommon0 \
    libxkbcommon-x11-0 \
    libxcb1 \
    libxcb-render0 \
    libxcb-shm0 \
    libxcb-icccm4 \
    libxcb-image0 \
    libxcb-keysyms1 \
    libxcb-randr0 \
    libxcb-shape0 \
    libxcb-xfixes0 \
    libxcb-xinerama0 \
    libxcb-xinput0 \
    libsm6 \
    libice6 \
    && rm -rf /var/lib/apt/lists/*

# install rust with rustup
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
ENV PATH="/root/.cargo/bin:${PATH}"

# Install uv and Python 3.13 with uv
RUN curl -LsSf https://astral.sh/uv/install.sh | sh \
    && ln -s /root/.local/bin/uv /usr/local/bin/uv
ENV PATH="/root/.local/bin:${PATH}"

RUN uv python install ${PYTHON_VERSION} --default

COPY . . 

RUN ./tools/build


# Install pre-compiled Anki.
FROM python:3.13-slim AS installer
WORKDIR /opt/anki/
COPY --from=build /opt/anki/out/wheels/ wheels/
# Use virtual environment.
RUN python -m venv venv \
    && ./venv/bin/python -m pip install --no-cache-dir setuptools wheel \
    && ./venv/bin/python -m pip install --no-cache-dir /opt/anki/wheels/*.whl


# We use another build stage here so we don't include the wheels in the final image.
FROM python:3.13-slim AS final
COPY --from=installer /opt/anki/venv /opt/anki/venv
ENV PATH=/opt/anki/venv/bin:$PATH
# Install run-time dependencies.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
    libasound2 \
    libdbus-1-3 \
    libfontconfig1 \
    libfreetype6 \
    libgl1 \
    libglib2.0-0 \
    libnss3 \
    libxcb-icccm4 \
    libxcb-image0 \
    libxcb-keysyms1 \
    libxcb-randr0 \
    libxcb-render-util0 \
    libxcb-shape0 \
    libxcb-xinerama0 \
    libxcb-xkb1 \
    libxcomposite1 \
    libxcursor1 \
    libxi6 \
    libxkbcommon0 \
    libxkbcommon-x11-0 \
    libxrandr2 \
    libxrender1 \
    libxtst6 \
    && rm -rf /var/lib/apt/lists/*

# Add non-root user.
RUN useradd --create-home anki
USER anki
WORKDIR /work
ENTRYPOINT ["/opt/anki/venv/bin/anki"]