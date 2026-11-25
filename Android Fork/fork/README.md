# Anki Time for Android

This folder ("Android Fork") is [AnkiDroid](https://github.com/ankidroid/Anki-Android) with the
RWKV time budgets of this repository. The APK is built by
[`.github/workflows/android.yml`](../../.github/workflows/android.yml) and published in the
repository's releases.

## What it adds

The scheduling itself is the same code as on the desktop: the app is built against this
repository's Rust core (`rslib`), which contains the RWKV-Instant engine with its bundled model
(`rslib/src/rwkv`) and the time-budget study queue (`rslib/src/scheduler/time_budget`). On top of
that, the app has:

- **RWKV Time Budgets** in the deck list's menu (⋮) and in each deck's long-press menu: minutes
  per day for each top-level deck, and, under advanced settings, max reviews per card per day,
  minimum/maximum DR, the card order and whether new cards stop at the deck's (and subdecks')
  daily new card limit. The settings are stored in the collection (config key
  `rwkvTimeBudget`), so they sync through AnkiWeb with the desktop app.
- **Deck list**: decks with a budget show the minutes left instead of the due counts, and 0 min
  once nothing is left today (worked out in the background, as on the desktop).
- **Completion screen**: once a budget deck is finished, the congrats page says the time goal
  was reached, with a link to keep reviewing for the rest of the day: cards are shown lowest
  predicted recall first, past the maximum DR, and new cards stay within their limits.
- **Study screen**: "Time left" and "Cards to study" instead of new/learning/review counts.
- **Reviewer**: the next batch of cards is scored while a question is shown, in both the classic
  and the new study screen.
- **Statistics**: the Retention Over Time graph, which comes with the core's web pages.

The RWKV state is saved next to the collection (`collection.rwkv`) when the collection closes,
as on the desktop. It is not synced: each device replays its review log once.

| File | Role |
| --- | --- |
| `AnkiDroid/src/main/java/com/ichi2/anki/timebudget/` | Settings, the dialog, status helpers |
| `AnkiDroid/src/main/res/values/time_budget.xml` | Strings (English) |
| `deckpicker/DisplayDeckNode.kt`, `deckpicker/DeckPickerViewModel.kt`, `widgets/DeckAdapter.kt` | Deck list |
| `DeckPicker.kt`, `dialogs/DeckPickerContextMenu.kt`, `contextmenu/DeckPickerMenuContentProvider.kt`, `res/menu*/deck_picker.xml` | Menu entries |
| `StudyOptionsViewModel.kt`, `StudyOptionsFragment.kt` | Study screen |
| `study/StudyCompletionUi.kt`, `pages/CongratsPage.kt` | Completion screen |
| `Reviewer.kt`, `ui/windows/reviewer/ReviewerViewModel.kt` | Prefetch |
| `AnkiDroid/build.gradle.kts` | `forkVersionCode`, `forkVersionName`, `forkAcraUrl` properties |
| `fork/build-apk.sh`, `fork/backend.patch` | Build |

## Installing

Download `AnkiTime-…-arm64-v8a.apk` from the newest `android-…` release and open it on the phone
(allow installing from your browser or file manager when asked). The app is called **Anki Time**
(`com.ichi2.anki.timefork`) and installs next to the official AnkiDroid; it has its own
collection, so log in to AnkiWeb and sync to bring your decks over.

Every push to `main` that changes the core or the app makes a new release with a higher version,
which installs over the previous one. To get updates automatically, add this repository to
[Obtainium](https://github.com/ImranR98/Obtainium).

## Signing key

An app can only be updated by an APK signed with the same key. Until you add your own key, the
workflow signs with AnkiDroid's public fallback key (`tools/fallback-release-keystore.jks`): updates
work, but anyone could sign an APK that installs over yours. To use your own key, create one once
and keep a backup of it:

```bash
keytool -genkeypair -v -keystore anki-time.jks -alias anki-time -keyalg RSA -keysize 4096 -validity 36500
```

Then add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | output of `base64 -w0 anki-time.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | `anki-time` |
| `ANDROID_KEY_PASSWORD` | the key password (if different from the keystore password) |

Switching keys means uninstalling once (sync first, so nothing is lost).

## Building locally

On Linux, with JDK 21, rustup, rsync and the Android SDK (`platforms;android-36`,
`platforms;android-37.0`, `build-tools;36.0.0`, `ndk;29.0.14206865`):

```bash
ANDROID_HOME=$HOME/Android/Sdk "Android Fork/fork/build-apk.sh"
```

The APK ends up in `out/android/apk/`. The script builds the committed core, so commit first.

## How the build fits together

AnkiDroid uses Anki's Rust core through
[Anki-Android-Backend](https://github.com/ankidroid/Anki-Android-Backend), which has the core as
its `anki` submodule. `build-apk.sh` checks out the backend at a fixed commit, puts this
repository in place of the submodule, applies `backend.patch` (build one architecture, skip the
unit-test library), builds it, and then builds the app against it (`local_backend=true`).

## Updating AnkiDroid

"Android Fork" is AnkiDroid at commit `27a954e10994872171420006c15e7b92db18a58a` (2.26.0alpha15)
without its git history; the changes above are on top of it. To move to a newer AnkiDroid:

1. Check which backend version it uses (`ankiBackend` in `gradle/libs.versions.toml`), and which
   Anki version that backend adopts. The core of this repository should be based on the same
   Anki version (merge it from upstream Anki first if needed), and `proto/` must match.
2. Replace this folder with the new AnkiDroid source, keeping `fork/`, and reapply the changes
   listed above. The commit "Add AnkiDroid 2.26.0alpha15 as "Android Fork"" holds AnkiDroid
   unmodified, so `git diff <that commit> -- "Android Fork"` shows every change.
3. Set `BACKEND_COMMIT` in `build-apk.sh` to the backend commit for that version.
