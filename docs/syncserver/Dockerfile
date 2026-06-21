FROM rust:1.92.0-alpine3.23 AS builder

ARG ANKI_VERSION

RUN apk update && apk add --no-cache build-base protobuf && rm -rf /var/cache/apk/*

RUN cargo install --git https://github.com/ankitects/anki.git \
--tag ${ANKI_VERSION} \
--root /anki-server  \
--locked \
anki-sync-server

FROM alpine:3.23

# Default PUID and PGID values (can be overridden at runtime). Use these to
# ensure the files on the volume have the permissions you need.
ENV PUID=1000
ENV PGID=1000

COPY --from=builder /anki-server/bin/anki-sync-server /usr/local/bin/anki-sync-server

RUN apk update && apk add --no-cache bash su-exec && rm -rf /var/cache/apk/*


EXPOSE 8080

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
CMD ["anki-sync-server"]

# Use the built-in healthcheck to avoid noisy HTTP log entries on every probe.
# The --healthcheck flag exits 0 if the server is healthy, 1 otherwise, and
# produces no stdout output — allowing Docker/k3s to surface health status
# without polluting container logs.
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD anki-sync-server --healthcheck

VOLUME /anki_data

LABEL maintainer="Jean Khawand <jk@jeankhawand.com>"
