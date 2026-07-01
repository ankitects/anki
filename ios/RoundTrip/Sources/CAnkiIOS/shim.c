// Intentionally empty. This C target exists only to expose the anki_ios.h
// module map to Swift; the actual symbols (anki_open, etc.) live in the
// prebuilt libanki_ios.a, linked via -L/-lanki_ios on the RoundTrip target.
#include "anki_ios.h"
