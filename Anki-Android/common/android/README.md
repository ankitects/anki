## AnkiDroid Common (Android)

Android-specific utilities which are generally applicable to AnkiDroid.

Split from `:common` to ensure that `:common` is a `java-library`, to support fast, pure-JVM tests.

## Packages

### `com.ichi2.anki.common.utils.android`

Android-specific utilities (e.g. `isRobolectric`)

### `com.ichi2.anki.common.utils.ext`

Extension methods on Android framework classes (e.g. `Intent`)

## Resource class references (`CommonR`)

Resources for this module live in `com.ichi2.anki.common.android.R` due to 
 `nonTransitiveRClass=true` (AGP default).

Use `CommonR` as an alias when outside this module. Inside this module, `R` should be used as the alias.

```
// outside this module
import com.ichi2.anki.common.android.R as CommonR

// inside this module
import com.ichi2.anki.common.android.R
```

## Future extensions

### Theming

See [docs/development/theming-modularization.md](../../docs/development/theming-modularization.md).

This module will define base themes: `Base.Theme.Light.Plain` etc... to be extended in `:AnkiDroid` 
with feature-level theme overlays.

`:common:android` should only contain the following for theming:

* well-known common values and attributes (Material Colors/attrs)
* values and attributes which are used by multiple features

```xml
<!-- GOOD: overridable per-feature -->
<attr name="appBarColor" format="color"/>

<!-- BAD: declare it in a ':study-screen' feature or :AnkiDroid if there is no feature module --> 
<attr name="showAnswerButtonBackground" format="color"/> 
```