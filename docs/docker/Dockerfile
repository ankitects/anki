# This Dockerfile uses three stages.
#   1. Compile anki (and dependencies) and build python wheels.
#   2. Create a virtual environment containing anki and its dependencies.
#   3. Create a final image that only includes anki's virtual environment and required
#      system packages.

ARG PYTHON_VERSION="3.9"
ARG DEBIAN_FRONTEND="noninteractive"

# Build anki.
FROM python:$PYTHON_VERSION AS build
RUN curl -fsSL https://github.com/bazelbuild/bazelisk/releases/download/v1.7.4/bazelisk-linux-amd64 \
    > /usr/local/bin/bazel \
    && chmod +x /usr/local/bin/bazel \
    # Bazel expects /usr/bin/python
    && ln -s /usr/local/bin/python /usr/bin/python
WORKDIR /opt/anki
COPY . .
# Build python wheels.
RUN ./tools/build

# Install pre-compiled Anki.
FROM python:${PYTHON_VERSION}-slim as installer
WORKDIR /opt/anki/
COPY --from=build /opt/anki/wheels/ wheels/
# Use virtual environment.
RUN python -m venv venv \
    && ./venv/bin/python -m pip install --no-cache-dir setuptools wheel \
    && ./venv/bin/python -m pip install --no-cache-dir /opt/anki/wheels/*.whl

# We use another build stage here so we don't include the wheels in the final image.
FROM python:${PYTHON_VERSION}-slim as final
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
LABEL maintainer="Jakub Kaczmarzyk <jakub.kaczmarzyk@gmail.com>"
