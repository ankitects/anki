## AnkiDroid Common (Android)

Android-specific utilities which are generally applicable to AnkiDroid.

Split from `:common` to ensure that `:common` is a `java-library`, to support fast, pure-JVM tests.

## Packages

### `com.ichi2.anki.common.utils.android`

Android-specific utilities (e.g. `isRobolectric`)

### `com.ichi2.anki.common.utils.ext`

Extension methods on Android framework classes (e.g. `Intent`)

## String resources

> [!NOTE]
> Files in `values-*` are owned by Crowdin. Do not edit these by hand.

The CrowdIn-managed string files (`res/values/01-core.xml` etc.) and their translations
(`res/values-*`) live in this module so they are usable from any feature module.

Translations are synced by [tools/localization](../../tools/localization/README.md).

## Resource class references

Resources for this module live in `com.ichi2.anki.common.android.R` due to 
 `nonTransitiveRClass=true` (AGP default).

### Helpers for strings (`CommonString`) and plurals (`CommonPlurals`)

Top-level typealiases in `com.ichi2.anki`
([StringResAliases.kt](src/main/java/com/ichi2/anki/StringResAliases.kt)):

```kotlin
import com.ichi2.anki.CommonString // a plain import: the IDE auto-inserts it
import com.ichi2.anki.CommonPlurals

getString(CommonString.card_browser)
getQuantityString(CommonPlurals.widget_cards_due, n, n)
```

Files in package `com.ichi2.anki` need no import.

### Preference keys (`CommonPreferenceKey`)

Use `CommonPreferenceKey` for non-translatable preference key resources shared by
Kotlin code and preference XML:

```kotlin
import com.ichi2.anki.CommonPreferenceKey

getString(CommonPreferenceKey.pref_new_review_reminders)
```

The key remains defined once in XML with `translatable="false"` and referenced
as `@string/pref_new_review_reminders` by the preference UI. `CommonPreferenceKey`
and `CommonString` alias the same generated `R.string` class. Their intended uses
are enforced by lint rather than the Kotlin compiler.

Define shared preference keys in `preferences.xml` or `preferences_*.xml`.
The `CommonPreferenceKeyUsage` lint check uses these resource files to enforce
the alias convention, including for keys whose names do not start with `pref_`.

### Other resource types (`CommonR`)

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

This module will define base themes: `Base.Theme.AnkiDroid.Plain` etc... to be extended in `:AnkiDroid` 
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
