FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive
WORKDIR /opt/anki
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl git build-essential pkg-config libssl-dev libbz2-dev libreadline-dev \
    libsqlite3-dev libffi-dev zlib1g-dev liblzma-dev ca-certificates ninja-build rsync \
    libglib2.0-0 libgl1 libx11-6 libxext6 libxrender1 libxkbcommon0 libxkbcommon-x11-0 \
    libxcb1 libxcb-render0 libxcb-shm0 libxcb-icccm4 libxcb-image0 libxcb-keysyms1 \
    libxcb-randr0 libxcb-shape0 libxcb-xfixes0 libxcb-xinerama0 libxcb-xinput0 libsm6 libice6 \
    libnss3 libegl1 libxcomposite1 libxcursor1 libxdamage1 libxi6 libxrandr2 \
    libxtst6 libxkbfile1 libxcb-cursor0 libxcb-render-util0 libminizip1 \
    libasound2t64 libpulse-mainloop-glib0 pulseaudio mpv lame fonts-dejavu-core \
    xvfb x11-utils x11vnc xdotool openbox novnc websockify ffmpeg scrot python3-tk \
    && rm -rf /var/lib/apt/lists/*
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain none
RUN curl -LsSf https://astral.sh/uv/install.sh | sh
ENV PATH=/root/.cargo/bin:/root/.local/bin:$PATH
RUN uv python install 3.13 --default
COPY . .
RUN --mount=type=cache,target=/opt/anki/out \
    --mount=type=cache,target=/root/.cargo/registry \
    --mount=type=cache,target=/root/.cargo/git \
    --mount=type=cache,target=/root/.cache/pip \
    rm -rf out/wheels && ./ninja wheels \
    && python3 -m venv /opt/agent-e2e \
    && /opt/agent-e2e/bin/pip install out/wheels/*.whl 'aqt[qt]' mcp==1.26.0 PyAutoGUI==0.9.54 'Pillow>=9.2'
ENV PATH=/opt/agent-e2e/bin:$PATH DISPLAY=:99 XDG_SESSION_TYPE=x11 XDG_RUNTIME_DIR=/tmp/runtime \
    QTWEBENGINE_CHROMIUM_FLAGS="--no-sandbox --disable-gpu"
WORKDIR /artifacts
ENTRYPOINT ["bash", "/harness/desktop.sh"]
