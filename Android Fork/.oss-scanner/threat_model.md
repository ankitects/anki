<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# AnkiDroid security audit

AnkiDroid is an Android flashcard application. It stores users' notes, media and
study history locally and can synchronize with AnkiWeb or a user-configured sync
server. Users import shared decks and render their HTML, CSS and JavaScript in
WebViews, so externally supplied content is an important attack surface.

## Inputs and boundaries

Treat external apps, deck authors and media/file suppliers as potential attackers.
Protect collection data, unrelated files, sync credentials and app capabilities
across the following major attack surfaces. Inspect the Android manifests and
implementation together for exported components, intent handling and URI grants.

### ContentProvider API

The exported `CardContentProvider` is a large API available to other apps. Its
contract includes notes, cards, note types/templates, decks, review operations and
media insertion. The manifest declares the dangerous `READ_WRITE_DATABASE`
permission, but the provider enforces it in code rather than through a manifest
permission on the provider. Audit authorization across every reachable operation
and URI, including bulk/batch calls and unsupported-operation paths. Check both
callers without the permission and callers granted the documented API access.

Inspect caller identity, permission revocation, URI routing, projections,
selection/sort arguments and `ContentValues` as attacker-controlled inputs.
Trace media insertion's source URI and preferred filename through file copying
and backend storage: a caller must not use AnkiDroid's privileges to read or write
unrelated files or access credentials. Distinguish the broad collection access
intentionally granted by the permission from capabilities outside that grant.
The debug build permits self-calls differently from release builds; permission
findings need a separate caller and confirmation of release behavior.

Start with `CardContentProvider` and `FlashCardsContract`.

### Multimedia import

Image, audio and video import is a separate entry point, including picker results,
shared content URIs, external editor results and the provider's media API. Treat
the stream contents, advertised size, MIME type, extension, display name and
other metadata as untrusted. A user selecting a file does not authorize arbitrary
filesystem access or trust its decoder inputs.

Trace URI permissions, temporary-file creation, filename handling, copying into
`collection.media`, image processing and playback. Investigate path traversal,
unintended overwrite, access through AnkiDroid to URIs the caller cannot read,
malformed media and resource exhaustion. Follow imported media into rendered
cards and synchronization; storing it in the collection does not make it trusted.
Start with the multimedia package, `NoteEditorMultimediaController`, the provider's
`insertMediaFile`, and `Media`.

### Text and package import

Audit CSV/TSV/text import as well as deck and collection packages (`.apkg`,
`.colpkg` and legacy `collection.apkg`). Follow files from Android intents and
content providers through importer selection, cache copies, parsing and collection
updates. Check inconsistent MIME types/extensions, encoded filenames, malformed
or oversized records, archive entries, media mappings and database contents.
Inspect extraction paths, decompression limits, unintended file replacement and
the distinction between adding a deck and replacing a collection.

Imported note fields, templates and media remain attacker-controlled when later
rendered, edited or synced. User confirmation of an import or collection
replacement is not consent to expose credentials or access unrelated app data.
Start with `ImportUtils`, `Import`, `BackendImporting`, `CsvImporter` and
`AnkiPackageImporterFragment`.
Follow calls into the native backend rather than stopping at the Android wrapper.

### AnkiWeb shared-deck downloads and sync

**Import and sync are a hard security boundary.** Users commonly import decks
from other authors on one device and sync them through their own AnkiWeb account
to AnkiDroid. A legitimate account, authenticated transport, a trusted sync server
or successful import does not make the deck author's content trusted. This
attacker model does not require compromising AnkiWeb or stealing an account.

Audit the shared-deck browser and download path as well as the package importer:
navigation and download URLs, redirects, cookie/header handling, server-supplied
filenames, download destinations and the handoff to import. AnkiWeb hosting a
shared deck does not grant that deck privileged access to the app.

Follow attacker-controlled notes, templates and media through import, upload,
download, media sync and later rendering on another device. Check that these
transitions preserve the same boundary around files, credentials, native calls
and JavaScript bridges. Also consider hostile responses from user-configured
sync servers. Start with `SharedDecksActivity`, `SharedDecksDownloadFragment`,
`Sync` and the sync workers.

### WebViews, JavaScript bridges and native backend

Anki pages and card viewers load from `https://appassets.androidplatform.net/`;
their WebView clients serve bundled assets and collection media locally. The app
no longer listens on a localhost HTTP port. `PostRequestBridge` replaces same-origin
`fetch()` POST calls with binary messages through an origin-scoped
`WebMessageListener`, then dispatches them to each view's `PostRequestHandler`.
Local asset loading and bridge calls do not require external networking or
loopback sockets.

Audit origin and frame restrictions, navigation, asset/media paths and raw bridge
messages as well as the `fetch()` wrapper. Treat request URIs and bodies as
attacker-controlled and trace the methods exposed by each view's handler and
JavaScript interfaces. Imported templates share the app content origin; that
origin alone does not establish trust. Card viewers allow mixed HTTP content and
persistent `localStorage`, so inspect external scripts, frames and data surviving
card/deck changes. Start with `PostRequestBridge`, `post-request-bridge.js`,
`PostRequestHandler`, `PageWebViewClient` and `ViewerResourceHandler`.

Card templates intentionally support JavaScript. Demonstrating that a card runs
JavaScript is not by itself a vulnerability; demonstrate an unintended capability,
such as escaping the intended bridge permissions, reading private app files, or
exposing credentials. State the attacker-controlled input, required user actions,
Android and WebView versions and permissions, and the resulting security impact.

This checkout contains the Android app and its Kotlin/Java libraries. The native
Anki backend is downloaded as a dependency from `ankidroid/Anki-Android-Backend`,
which incorporates `ankitects/anki`. Identify the responsible component when a
finding crosses that boundary. For a suspected upstream Anki issue, aim for a
small reproduction in `ankitects/anki` and keep the report concise.

## Reproducing findings offline

The checkout is at `/src`, the Android SDK at `/opt/android-sdk`, and the Gradle
cache (including the pinned JDK and Robolectric runtimes) at `/opt/gradle`.

```sh
./gradlew --offline --no-daemon :AnkiDroid:assemblePlayDebug
./gradlew --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process \
  jacocoUnitTestReport
./gradlew --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process \
  :AnkiDroid:testPlayDebugUnitTest --tests 'fully.qualified.TestClass'
./gradlew --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process \
  :libanki:test --tests 'fully.qualified.TestClass'
```

The test commands use in-process Kotlin compilation to avoid retaining separate
compiler heaps alongside the test JVM on the 8 GiB audit machine.

The APK is under `AnkiDroid/build/outputs/apk/play/debug/`. Unit test reports are
under each module's `build/reports/tests/`, and the combined coverage report is
under `AnkiDroid/build/reports/jacoco/jacocoUnitTestReport/`. Tests run on the JVM
with Robolectric; this image does not include an Android emulator. Device-only
behavior may need separate confirmation on Android.
WebView reproductions need version 116 or later with the message-listener,
document-start-script and array-buffer APIs required by `PostRequestBridge`.

Prefer a minimal reproducer and a focused regression test. Explain observed
behavior separately from any impact that still requires confirmation. Use
synthetic collections and credentials in reproductions.
