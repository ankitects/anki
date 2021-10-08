ARG PYTHON_VERSION="3.9"

FROM python:$PYTHON_VERSION AS build
# Install bazel.
RUN curl -fsSL https://github.com/bazelbuild/bazelisk/releases/download/v1.7.4/bazelisk-linux-amd64 \
    > /usr/local/bin/bazel \
    && chmod +x /usr/local/bin/bazel \
    # Bazel excepts /usr/bin/python
    && ln -s /usr/local/bin/python /usr/bin/python
WORKDIR /opt/anki
COPY . .
# Build python wheels.
RUN ./scripts/build

FROM python:${PYTHON_VERSION}-slim
# Install pre-compiled Anki.
WORKDIR /opt/anki/
COPY --from=build /opt/anki/bazel-dist/ wheels/
# Use virtual environment.
ENV PATH=/opt/anki/venv/bin:$PATH
RUN python -m venv venv \
    && /opt/anki/venv/bin/python -m pip install --no-cache-dir setuptools wheel \
    && /opt/anki/venv/bin/python -m pip install --no-cache-dir /opt/anki/wheels/*.whl
# Install run-time dependencies.
RUN apt-get update \
    && DEBIAN_FRONTEND="noninteractive" apt-get install --yes --no-install-recommends \
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
ENTRYPOINT ["/opt/anki/venv/bin/anki"]
LABEL maintainer="Jakub Kaczmarzyk <jakub.kaczmarzyk@gmail.com>"
