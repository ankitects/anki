// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#ifndef ANKI_BACKEND_H
#define ANKI_BACKEND_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  const uint8_t *data;
  size_t len;
} AnkiByteSlice;

// A token of zero represents an empty, unowned buffer. For non-zero tokens,
// data remains readable until anki_backend_buffer_free() is called.
typedef struct {
  const uint8_t *data;
  size_t len;
  uint64_t token;
} AnkiOwnedBuffer;

typedef struct {
  uint64_t handle;
  AnkiOwnedBuffer error;
} AnkiBackendOpenResult;

typedef struct {
  AnkiOwnedBuffer output;
  AnkiOwnedBuffer error;
} AnkiBackendCallResult;

// Non-empty input slices must point to len readable bytes for the duration of
// the call. Error buffers contain serialized anki.backend.BackendError bytes.
AnkiBackendOpenResult anki_backend_open(AnkiByteSlice input);
AnkiOwnedBuffer anki_backend_close(uint64_t handle);
AnkiBackendCallResult anki_backend_run_method(uint64_t handle, uint32_t service,
                                              uint32_t method,
                                              AnkiByteSlice input);

// Safe to call with a zero, unknown, or previously released token.
void anki_backend_buffer_free(AnkiOwnedBuffer buffer);

#ifdef __cplusplus
}
#endif

#endif // ANKI_BACKEND_H
