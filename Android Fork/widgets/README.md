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

The widget providers, their configuration screens and their manifest declarations all live here.
What is left in `:AnkiDroid`:

- `MetaDbWidgetRepository`, the app's `WidgetRepository` implementation.
- Most widget tests, which need `RobolectricTest` or other test helpers from the app.

Remaining work is tracked under #20737.
