# `:libanki`

A 1:1 port of Anki's [`pylib`](https://github.com/ankitects/anki/tree/main/pylib), defining core domain types and wrapping the Rust backend.

## Aims

`:libanki` aims to mirror upstream `pylib`, so it is easy to bring across any future changes.

One of the following annotations should be used on all new public code:

- Use [`@LibAnkiAlias`](src/main/java/com/ichi2/anki/libanki/utils/LibAnkiAlias.kt) to indicate which upstream method is replicated. The implementation should follow the upstream implementation as close as possible, discrepancies should be described in the documentation.
  - Kotlin uses `camelCase`, Python uses `snake_case`, this allows for easy mapping
- Use [`@NotInPyLib`](src/main/java/com/ichi2/anki/libanki/utils/NotInPyLib.kt) for AnkiDroid-specific additions with no `pylib` equivalent.
  - Functionality needs **strong** justification as to why it can't exist in `:anki-common` 
  (if shared between features), a feature module, or `:AnkiDroid`.

## See also

- [`anki:pylib`](https://github.com/ankitects/anki/tree/main/pylib) - where this code is ported from.
- [`anki:proto`](https://github.com/ankitects/anki/tree/main/proto) - function definitions, exposed
 via `.backend` (`net.ankiweb.rsdroid.Backend`).
- [`anki:rslib`](https://github.com/ankitects/anki/tree/main/rslib) - the implementation of the functions (Rust-based).
- [Anki-Android-Backend](https://github.com/ankidroid/Anki-Android-Backend/) - packages the Rust backend (`rsdroid`); generates Kotlin code 
  from types in `anki:proto` and defines an Android-based database abstraction.

