FROM debian:11-slim

ARG DEBIAN_FRONTEND="noninteractive"
ENV PYTHON_SITE_PACKAGES=/usr/lib/python3/dist-packages/

RUN useradd -d /state -m -u 998 user

RUN apt-get update && apt install --yes gnupg ca-certificates && \
    apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 32A37959C2FA5C3C99EFBC32A79206696452D198 \
    && echo "deb https://apt.buildkite.com/buildkite-agent stable main" > /etc/apt/sources.list.d/buildkite-agent.list \
    && apt-get update \
    && apt-get install --yes --no-install-recommends \
    autoconf \
    bash \
    buildkite-agent \
    ca-certificates \
    curl \
    findutils \
    g++ \
    gcc \
    git \
    grep \
    libdbus-1-3 \
    libegl1 \
    libfontconfig1 \
    libgl1 \
    libgstreamer-gl1.0-0 \
    libgstreamer-plugins-base1.0 \
    libgstreamer1.0-0 \
    libnss3 \
    libpulse-mainloop-glib0 \
    libpulse-mainloop-glib0 \    
    libssl-dev \
    libxcomposite1 \
    libxcursor1 \
    libxi6 \
    libxkbcommon-x11-0 \
    libxkbcommon0 \
    libxkbfile1	\
    libxrandr2 \
    libxrender1 \
    libxtst6 \
    make \
    pkg-config \
    portaudio19-dev \
    python3-dev \
    rsync \
    # -- begin only required for arm64/debian11
    clang-format \
    python-is-python3 \
    python3-pyqt5.qtwebengine \
    # -- end only required for arm64/debian11    
    && rm -rf /var/lib/apt/lists/*


RUN curl -L https://github.com/bazelbuild/bazelisk/releases/download/v1.10.1/bazelisk-linux-arm64 \
    -o /usr/local/bin/bazel \
    && chmod +x /usr/local/bin/bazel

RUN ln -sf /usr/bin/python3 /usr/bin/python

RUN mkdir -p /etc/buildkite-agent/hooks && chown -R user /etc/buildkite-agent

COPY buildkite.cfg /etc/buildkite-agent/buildkite-agent.cfg
COPY environment /etc/buildkite-agent/hooks/environment

USER user
WORKDIR /code/buildkite
ENTRYPOINT ["/usr/bin/buildkite-agent", "start"]
