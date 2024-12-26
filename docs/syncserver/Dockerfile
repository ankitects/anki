FROM rust:1.82.0-alpine3.20 AS builder

ARG ANKI_VERSION

RUN apk update && apk add --no-cache build-base protobuf && rm -rf /var/cache/apk/*

RUN cargo install --git https://github.com/ankitects/anki.git \
--tag ${ANKI_VERSION} \
--root /anki-server  \
anki-sync-server

FROM alpine:3.20.2

RUN adduser -D -h /home/anki anki

COPY --from=builder /anki-server/bin/anki-sync-server /usr/local/bin/anki-sync-server


RUN apk update && apk add --no-cache bash && rm -rf /var/cache/apk/*

USER anki

ENV SYNC_PORT=${SYNC_PORT:-"8080"}

EXPOSE ${SYNC_PORT}

CMD ["anki-sync-server"]

# This health check will work for Anki versions 24.08.x and newer.
# For older versions, it may incorrectly report an unhealthy status, which should not be the case.
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1:${SYNC_PORT}/health || exit 1

LABEL maintainer="Jean Khawand <jk@jeankhawand.com>"
