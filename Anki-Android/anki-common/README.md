# `:anki-common`

This module exists to break circular dependencies when `:AnkiDroid` is split into feature modules.

## What belongs here

- Common types and features which depend on `:libanki` and are not part of [`pylib`](https://github.com/ankitects/anki/blob/main/pylib/)
- Common types which depend on `:compat`.
  - To ensure there's no dependency from `:common` -> `:compat`.

## What does not belong here

- Feature-specific business logic should be in `:AnkiDroid` or feature modules.
- Common code which does not depend on `:libanki` should be in `:common`

## Why this module exists

- `:libanki` aims to be a 1:1 implementation of upstream `pylib` which defines domain types (`CardId` etc...).
  - AnkiDroid specific extensions need strong justification for inclusion in `:libanki`
- `:libanki` depends on `:common`. Common code which needs domain types would cause a circular dependency if placed in `:common`.

## Future direction

This module is currently an Android library because `:libanki` is.
When `:libanki` becomes a JVM library, this module splits into a pure-JVM half and an Android half (mirroring the existing `:common` / `:common:android` pair), with the JVM half holding everything that doesn't require the Android framework.
