# Jetpack Compose in AnkiDroid

> ⚠️ **Work in progress.** This is a living draft. Direction, conventions and timing here can change as the discussion evolves. Don't treat anything on this page as final

This page is a short note on how AnkiDroid is approaching Jetpack Compose. It covers where we are today, what's being deferred, and the conventions to follow if you do end up writing Compose code in the meantime.

## Where we are right now

Compose is something we're moving toward gradually, not migrating to wholesale. The current direction:

- When a new screen actually appears, it should default to Compose.
- An existing screen can use Compose if it's already being significantly changed for another reason (refactor, redesign, a large feature, a bug-heavy area).
- We are not actively migrating existing screens that work today. Pure "let's migrate this XML screen to Compose" PRs aren't being accepted right now. Review bandwidth is finite, there are 250+ open issues and a backlog of PRs that produce direct user value, and a churn migration with no visible user benefit isn't where that bandwidth should go.

## Why we care at all

Google declared Android UI "Compose First" at I/O 2026. In practice that means:

- Existing Views, Fragments, RecyclerView, ViewPager etc. are in maintenance mode. That means no new features and only critical bug fixes. They are not deprecated and they are not being removed.
- New platform features ship Compose-only or Compose-first. Glance widgets, Wear Widgets, adaptive layouts for foldables, the new Android Studio UI tooling, the Style API, Grid all of these are Compose-first.

So Views aren't going away. AnkiDroid in pure XML will keep working on new Android versions for years. The reason to be Compose-capable is so the project can pick up new platform features when it wants to, without it turning into a forced last-minute rewrite.

## What we are explicitly not doing

To save back-and-forth on issues, the things that are off the table for now:

- No mandate. No roadmap. No deadlines.
- No "migrate screen X to Compose" PRs without another reason to touch the screen.
- No new `:ui-compose` Gradle module yet. That decision is parked until there's a concrete second consumer (for example, if `:AnkiDroid` ever gets split into feature modules).

## Where Compose code lives today

For now, all Compose code lives inside `:AnkiDroid` at `com.ichi2.anki.ui.compose.*`(Proposed):

```
AnkiDroid/src/main/java/com/ichi2/anki/ui/compose/
├── theme/
├── composables/      
└── ...
```

Treat that package as a future extraction target. In practice that means:

- Don't import from feature packages (`com.ichi2.anki.reviewer`, `com.ichi2.anki.deckpicker`, etc.) inside this package.
- Keep the public API deliberate. Anything exported is going to be relied on later.
- Resources used by these composables (string resources, theme attrs) should be thought of as living with this package, even though `res/` is shared.

If a concrete second consumer ever appears, extracting the whole package into `:ui-compose` becomes essentially a `git mv` plus a Gradle dependency line. If we never need to, no harm done.

## Conventions

A handful of things to follow when writing Compose in AnkiDroid.

**Naming.** Don't prefix component names with `Anki`. Everything in this repo is already AnkiDroid; the prefix doesn't add information. Name things by what they are (`Theme`, `SectionHeader`, `HtmlText`) and let the package path do the namespacing. If a name would collide with a Material3 component, either use the Material3 component directly or pick a name that describes what's actually different.

**Theme.** Wrap top-level composables in the project's `Theme { ... }`. It reads the active AnkiDroid XML theme (light, plain, dark, black) and surfaces those colors as a Material3 `ColorScheme`. Don't define a parallel set of color tokens in Kotlin. The XML themes stay the source of truth, and a theme change in app settings (which already calls `recreate()`) propagates automatically.

**Hosting Compose in a Fragment.** Almost all AnkiDroid screens today are Fragments, and that's not going to change. New Compose UI usually lives inside a Fragment shell that returns a `ComposeView` from `onCreateView`. That keeps the surrounding navigation, lifecycle and back-stack code untouched.

**Icons.** Use existing project drawables via `painterResource(R.drawable.xxx)`. Don't add `androidx.compose.material:material-icons-core` or `-extended`. The drawables we already ship cover what we need, and the icons artifact would only grow the build.

**Migrations include a screenshot test first.** If you're rewriting an existing screen in Compose (because it's already getting significant changes for another reason), the recommended pattern is two PRs: first add a Roborazzi screenshot test for the existing XML version so we have a visual baseline, then do the Compose rewrite on top of that. The first PR is a useful standalone contribution; the second one becomes a lot less risky to review.

**Doing one thing per migration.** When a screen is being moved to Compose, that PR should not also restructure state management, introduce new architectural patterns, or rework navigation. One change at a time. This is the same lesson from the Java-to-Kotlin conversion: combining cleanup goals with the migration is what makes them never finish.

## References

- [Android UI Development is Compose First](https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html) (Android Developers Blog, May 2026)
- [What's new in the Jetpack Compose April '26 release](https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html)
- [Interoperability APIs](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis) (`AndroidView`, `ComposeView`)
