## `com.ichi2.anki.backend`

This package makes the Anki backend (`com.ichi2.anki.libanki`) more easily consumable by an Android app.
 It does so by adding helper methods, extension methods, and functionality which depends on Android.

`libanki` is expected to match `anki/pylib`, with minimal Android dependencies and no `Anki-Android` 
 dependencies. As `com.ichi2.anki.backend` is in `Anki-Android`, these constraints do not apply.