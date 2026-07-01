// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
//
// C ABI header for the anki-ios staticlib. Copy of rslib/ios/anki_ios.h /
// ios/include/anki_ios.h, vendored into this SwiftPM C target so Swift can
// `import CAnkiIOS` and call the four exported functions. The matching
// libanki_ios.a (built with `cargo build -p anki-ios --target
// aarch64-apple-ios-sim`) is linked via -L/-lanki_ios linker flags on the
// RoundTrip executable target (see Package.swift).

#ifndef ANKI_IOS_H
#define ANKI_IOS_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct AnkiBackend AnkiBackend;

AnkiBackend *anki_open(const uint8_t *init_msg, size_t init_msg_len);

int32_t anki_command(AnkiBackend *backend, uint32_t service, uint32_t method,
                     const uint8_t *input, size_t input_len,
                     uint8_t **out_ptr, size_t *out_len);

void anki_free(uint8_t *ptr, size_t len);

void anki_free_backend(AnkiBackend *backend);

#ifdef __cplusplus
}
#endif

#endif // ANKI_IOS_H
