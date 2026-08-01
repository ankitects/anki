## AnkiDroid Common

AnkiDroid Common is a pure JVM [Gradle module](https://developer.android.com/topic/modularization) 
containing utility functions, and definitions for core functionality used by other modules 
within AnkiDroid. Common should be the base of the AnkiDroid dependency tree.

Common has no Android dependencies. Code requiring Android APIs belongs in `:common:android`.

This module is expected to define interfaces which are initialized in the `AnkiDroid` module

## Packages

### `com.ichi2.anki.common` 

Definitions/interfaces exposing core functionality e.g. `CrashReportService`, `UsageAnalytics`

These are to be initialized higher up the dependency tree, typically in `AnkiDroid`

### `com.ichi2.anki.common.utils`

Utility classes and methods

### `com.ichi2.anki.common.utils.ext` 

Extension methods, universally applicable to the classes they extend

## Context

As discussed in 
[#12582](https://github.com/ankidroid/Anki-Android/issues/12582), AnkiDroid decided to split the 
codebase into two modules, `libAnki` (business logic) and `AnkiDroid` (code interacting with 
Android APIs). `common` existed for logic which both `AnkiDroid` and `libAnki` depended on.  

Later,  `compat` was split out, also depending on `common`, solidifying `common`  

[Backend - #647](https://github.com/ankidroid/Anki-Android-Backend/issues/674): `libAnki` is 
intended to be converted to a `java-library`, so `:common` was split into `:common:android`, 
ensuring that


Discussed on Discord: https://discord.gg/qjzcRTx

* Discussion: https://discord.com/channels/368267295601983490/701922522836369498/1243991110888591482
* Thread: https://discord.com/channels/368267295601983490/1244372448233914438
* https://github.com/ankidroid/Anki-Android/pull/16498
* [#20547 - extract `:compat`](https://github.com/ankidroid/Anki-Android/issues/20547)
