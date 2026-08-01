# `:widgets`

Home for AnkiDroid's app-widget code (home screen widgets and their configuration screens),
extracted from `:AnkiDroid` so widgets can be built and tested independently of the app module.

## What belongs here

- App-widget providers, their config activities, and widget-only helpers.
- Widget `<receiver>` / `<activity>` declarations (in this module's `AndroidManifest.xml`).

## What does not belong here

- Code shared with the rest of the app: it should live in a lower module
  (`:common`, `:common:android`, `:libanki`, `:anki-common`), not here.

## Plans

This module is created first, holding only files that already had zero `:AnkiDroid`
dependencies. The remaining widget code under `AnkiDroid/src/main/java/com/ichi2/widget`
still depends on `:AnkiDroid` (e.g. `CollectionManager`, `R`, `AnkiActivity`, snackbars),
so it is moved incrementally:

1. Push shared dependencies down to lower modules (tracked under #20737).
2. Relocate each widget file here once its dependencies are reachable.
3. Move the widget declarations from `:AnkiDroid`'s `AndroidManifest.xml` into this module.
