FROM rust:1.76-alpine3.19 AS builder

ARG ANKI_VERSION="23.12.1"

RUN apk update && apk add --no-cache build-base protobuf && rm -rf /var/cache/apk/*
RUN cargo install --git https://github.com/ankitects/anki.git \
    --tag ${ANKI_VERSION} \
    --root /anki-server  \
    anki-sync-server

FROM alpine:3.19.1

RUN adduser -D -h /home/anki anki

COPY --from=builder /anki-server/bin/anki-sync-server /usr/local/bin/anki-sync-server


RUN apk update && apk add --no-cache bash && rm -rf /var/cache/apk/*

USER anki

ENV SYNC_PORT=${SYNC_PORT:-"8080"}

EXPOSE ${SYNC_PORT}

CMD ["anki-sync-server"]

# TODO - consider exposing endpoint /health to check on health cause currently it will return 404 error
# HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
#     CMD wget -qO- http://localhost:${SYNC_PORT} || exit 1

LABEL maintainer="Jean Khawand <jk@jeankhawand.com>"