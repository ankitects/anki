FROM rust AS builder
SHELL ["/bin/bash", "-uo", "pipefail", "-c"]

ENV TARGET x86_64-unknown-linux-musl
RUN rustup target add "$TARGET"

RUN apt-get -y update && apt-get -y install protobuf-compiler musl musl-dev musl-tools
ENV PROTOC=/usr/bin/protoc
RUN cargo install --locked --target "$TARGET" --git https://github.com/ankitects/anki.git --tag 23.10 anki-sync-server
RUN ldd /usr/local/cargo/bin/anki-sync-server


FROM scratch
ENV SYNC_BASE=/data
COPY --from=builder --chmod=0755 /usr/local/cargo/bin/anki-sync-server /
CMD ["./anki-sync-server"]
