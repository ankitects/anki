# Build notes (fork-specific)

Verified on a clean checkout of this fork, 2026-07-30/31.

These notes supplement upstream's [development.md](./development.md) and
[mac.md](./mac.md); they do not replace them. They record what was actually
required on a real machine, what the build provisions for itself, and the
verified status of the desktop, Android and iOS targets.

**Scope of this document:** toolchain and build only. No product behaviour is
described or changed here.

---

## 1. Verified environment

|                           |                                                                  |
| ------------------------- | ---------------------------------------------------------------- |
| Machine                   | Apple Silicon (arm64), macOS 26.5.2 (25F84)                      |
| Rust                      | rustc/cargo **1.97.1** (Homebrew)                                |
| Xcode                     | **Command Line Tools only** — Apple clang 21.0.0. No full Xcode. |
| Repo version (`.version`) | `26.05`                                                          |

---

## 2. What you actually have to install

Only **two** tools were missing. Everything else the build downloads itself.

```bash
brew install ninja just
```

| Installed | Version    | Why                                                                                                                                                             |
| --------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ninja`   | **1.13.2** | The build system emits `build.ninja` and shells out to a ninja-compatible runner. Upstream docs accept Ninja 1.10+ from Homebrew, or n2 via `tools/install-n2`. |
| `just`    | **1.57.0** | Command runner. `CLAUDE.md` mandates the `just` recipes over calling `./ninja` / `./run` directly.                                                              |

### protoc is NOT a system dependency

Worth stating plainly, because it is easy to assume otherwise: **you do not need
to install protoc.** `build/ninja_gen/src/protobuf.rs:112` (`setup_protoc`)
downloads and extracts a pinned **protoc 31.1** universal binary into
`out/extracted/protoc/` on first build. Installing a system protoc has no
effect unless you explicitly set `PROTOC_BINARY` to an absolute path.

### Also auto-provisioned — do not install these by hand

The build downloads its own copies into `out/`, ignoring whatever is on `PATH`:

| Tool   | Version fetched                 | Location                |
| ------ | ------------------------------- | ----------------------- |
| protoc | 31.1                            | `out/extracted/protoc/` |
| Node   | v22.17.0                        | `out/extracted/node/`   |
| uv     | 0.11.8                          | `out/extracted/uv/`     |
| Python | 3.13.13 (per `.python-version`) | `out/pyenv/`            |

The system's Node 22.22.2 and Python 3.14.6 are **not used by the build**. Do
not spend time matching them.

Git submodules (`ftl/core-repo`, `ftl/qt-repo`) are checked out automatically by
the build — no manual `git submodule update` needed.

### Optional

`mpv` and `lame` for audio playback (`brew install mpv lame`). `lame` and
`ffmpeg` were already present here; `mpv` was not. Not required to build or
launch.

### Side effect to be aware of

Homebrew's post-install cleanup ran automatically during `brew install ninja
just` and **autoremoved `unbound` 1.25.1** as an unneeded formula (no remaining
dependents), plus pruned its own download cache. This was not requested. If you
need `unbound` back: `brew install unbound`.

---

## 3. Desktop build — GREEN, verified running

### Commands

```bash
brew install ninja just     # one-time
just build                  # build pylib + qt
just run                    # build and launch
```

To launch against a throwaway collection instead of your real one — strongly
recommended during development:

```bash
just run -b /tmp/ankibase
```

### Evidence it actually launches

`just run` was executed and Anki started. Observed, not inferred:

- `Starting Anki 26.05...` on stdout
- Qt initialised; Chromium remote debugging server bound to `127.0.0.1:8080`
- Three live webview targets on the CDP endpoint — `top toolbar`,
  `main webview`, `bottom toolbar` — i.e. the real main window
- The deck browser rendered: `mediasrv` served `deckbrowser.css`,
  `deckbrowser.js`, `toolbar.css`, `toolbar-bottom.css`, `gears.svg`,
  `refresh.svg`, `webview.js`
- Process alive under `out/pyenv/bin/python tools/run.py`

The app was then stopped cleanly.

Note: `http://localhost:40000/_anki/pages/*.html` (mentioned in `CLAUDE.md`)
404s at idle — those routes are only mounted once the corresponding screen is
opened. A 404 there is **not** a sign of a broken build.

### Build times (Apple Silicon, measured)

| Scenario                                                    | Wall clock     |
| ----------------------------------------------------------- | -------------- |
| **Cold** — empty `out/`, cold cargo registry for this tree  | **2 min 20 s** |
| No-op rebuild (nothing changed)                             | **0.5 s**      |
| Warm rebuild, comment-only change to `rslib/src/lib.rs`     | **3.7 s**      |
| **Warm rebuild, real codegen change to `rslib/src/lib.rs`** | **~5 s**       |

The ~5 s figure is the one that matters for planning: that is the edit→run loop
cost for a change to the Rust backend. Cold build produces a ~4.0 GB `out/`.

Cold build is dominated by fetching/compiling crates; the ninja graph itself
(65 targets) finishes in ~95 s once dependencies are in place.

### Caveat: Rust version vs `rust-toolchain.toml`

`rust-toolchain.toml` pins channel **1.92.0**, but that file is only honoured by
`rustup`. This machine has **no rustup** — Rust comes from Homebrew — so the
build ran on **1.97.1** and the pin was silently ignored.

The build is green on 1.97.1. Upstream's own warning applies: _newer Rust
versions typically work for building but may fail clippy/tests_. If you hit
clippy failures that look version-driven, install rustup and let the pin take
effect.

### `just check` status — three environmental gotchas, all diagnosed

`just check` was run to validate the toolchain more deeply. It exposed three
failures. **None is a code defect, and none was "fixed" by editing upstream
source.** Two are resolved; the third is the Rust-version pin above.

**1. `check:minilints` — `cargo-license` not found. RESOLVED.**

```
Error: Failed to execute: cargo-license --features rustls ... (os error 2)
```

`tools/minilints/src/main.rs:297` runs `cargo install cargo-license@0.7.0`,
which succeeds and lands the binary in `~/.cargo/bin` — but with Homebrew Rust
that directory is not on `PATH` (a rustup install would have added it). Fix:

```bash
export PATH="$PATH:$HOME/.cargo/bin"
```

`just minilints` then passes in ~2 s.

**2. Two `qt/tests/test_installer.py` failures — missing submodule. RESOLVED.**

```
Unable to clone application template; is the template path
'.../qt/installer/mac-template' correct?
```

The build auto-checks-out only `ftl/core-repo` and `ftl/qt-repo`. The Briefcase
installer templates are **not** initialised automatically. Fix:

```bash
git submodule update --init qt/installer/mac-template
```

All 27 installer tests then pass. Worth noting: **the macOS installer builds
without full Xcode** — Briefcase plus Command Line Tools is sufficient. Xcode is
an iOS blocker, not a desktop-installer one.

**3. `check:clippy` — fails on Rust 1.97.1. NOT resolved, and must not be.**

Two upstream files trip lints that postdate the 1.92.0 pin:

- `build/ninja_gen/src/git.rs:78` — `clippy::question_mark`
- `build/ninja_gen/src/input.rs:84` — `clippy::useless_borrows_in_formatting`

This is precisely the failure mode upstream documents for newer Rust. The
correct fix is **rustup, so the 1.92.0 pin applies** — not editing upstream
source. Lint-fixing these would create upstream merge conflicts for no gain.

With `PATH` fixed and the submodule initialised, **every other check passes**:
`mypy`, `ruff`, `eslint`, `svelte`, `typescript`, `format`, `rust_test`,
`pytest`, `vitest`, `minilints`.

---

## 4. Android — assessed, blocked on this machine

AnkiDroid lives in a separate repository and is **not** cloned or vendored here.

### How a change in this fork reaches an Android build

This is the part that is easy to get wrong: **AnkiDroid does not consume this
repository directly.** There is a third repo in the middle.

```
speedrun (this fork: rslib)
    └─ git submodule `anki` of ─────────► ankidroid/Anki-Android-Backend
                                              └─ rslib-bridge/  (JNI cdylib)
                                              └─ builds rsdroid-release.aar
                                                     │
                                                     ▼
                                          ankidroid/Anki-Android
```

`Anki-Android-Backend` carries `anki` as a git submodule pointing at
`https://github.com/ankitects/anki`. `rslib-bridge` is a thin JNI `cdylib`
exporting `Java_net_ankiweb_rsdroid_NativeMethods_*` symbols that wrap
`anki::backend::init_backend` and pass protobuf bytes in/out.

Concrete procedure to get this fork's Rust onto Android:

1. Fork `ankidroid/Anki-Android-Backend`; repoint its `anki` submodule at this
   fork and the branch you want.
2. Build the AAR: `./build.sh` (which sources `set-android-ndk-home.sh`, then
   runs `cargo run -p build_rust`).
3. In `Anki-Android`, create `local.properties` containing
   `local_backend=true`. `AnkiDroid/build.gradle:512-514` then links
   `../Anki-Android-Backend/rsdroid/build/outputs/aar/rsdroid-release.aar`
   instead of the published Maven artifact. The two repos must be siblings on
   disk.

### Exact requirements

| Requirement              | Version                                                                                                    | Present here?             |
| ------------------------ | ---------------------------------------------------------------------------------------------------------- | ------------------------- |
| **rustup**               | any                                                                                                        | ❌ **absent**             |
| Rust Android std targets | `aarch64-linux-android`, `armv7-linux-androideabi`, `i686-linux-android`, `x86_64-linux-android`           | ❌ **absent**             |
| Android NDK              | **29.0.14206865** (exact, from backend `gradle/libs.versions.toml`)                                        | ❌ absent                 |
| Android SDK              | compileSdk 36 / targetSdk 36 / minSdk 23 (backend); compileSdk 36 / targetSdk 35 / minSdk 24 (AnkiDroid)   | ❌ absent                 |
| JDK                      | **21** (AnkiDroid Gradle daemon toolchain, vendor JETBRAINS); source/target 17 in AnkiDroid, 11 in rsdroid | ❌ **no JVM at all**      |
| Gradle                   | 9.5.1 (backend wrapper), 9.6.0 (AnkiDroid wrapper)                                                         | ✅ wrapper self-downloads |
| Kotlin                   | 2.2.10 (backend), 2.3.21 (AnkiDroid)                                                                       | ✅ via Gradle             |
| Backend Rust toolchain   | 1.92.0 (`rust-toolchain.toml`)                                                                             | ⚠️ ignored without rustup  |

### Verified blockers

**1. No JVM.** `/usr/libexec/java_home -V` → _"Unable to locate a Java Runtime."_
No `adb`, `sdkmanager`, `gradle`, `kotlinc`, no Android Studio, no
`ANDROID_HOME`/`ANDROID_SDK_ROOT`, no `~/Library/Android/sdk`.

**2. No rustup, and therefore no Android Rust targets.** Verified by attempting
a real cross-compile, not assumed:

```
$ cargo build --target aarch64-linux-android
error[E0463]: can't find crate for `core`
  = note: the `aarch64-linux-android` target may not be installed
  = help: consider downloading the target with `rustup target add aarch64-linux-android`
```

Homebrew's Rust ships only the host `aarch64-apple-darwin` std and has no
mechanism to add targets. `Anki-Android-Backend`'s `build_rust/src/main.rs`
shells out to `rustup target add` directly, so it cannot run without rustup —
installing rustup is mandatory for this path, not optional.

**3. Version skew between the backend's pin and this fork.** The backend's
`anki` submodule is pinned at `e64c6b1ae` (2026-06-15). Against this fork's
HEAD (`4a8673634`) the two have **diverged: 23 commits on the pinned side that
this fork lacks, 86 commits here that the pin lacks.** Both report `.version`
`26.05`, so they are the same release line, but `rslib-bridge` is written
against an rslib ~86 commits older and may need fixes when repointed. Separately,
the _published_ artifact AnkiDroid consumes by default is
`io.github.david-allison:anki-android-backend:0.1.64-anki25.09.2` — an older
anki line still (25.09.2).

### Honest cost

Roughly **10–20 GB** of downloads (JDK 21, Android SDK 36, NDK 29.0.14206865,
Gradle, rustup + 4 Android std targets) and a few hours of setup, plus unknown
additional time to fix any `rslib-bridge` compile breakage caused by the
86-commit skew. Nothing here is technically blocked — it is all installable
without Apple gatekeeping — but **none of it is installed and none of it was
verified end to end on this machine.** Treat the "it will build once installed"
part as unverified.

---

## 5. iOS — assessed, hard-blocked

### Two independent blockers

**1. Xcode is not installed.** Only Command Line Tools:

```
$ xcode-select -p
/Library/Developer/CommandLineTools
$ xcodebuild -version
xcode-select: error: tool 'xcodebuild' requires Xcode, but active developer
directory '/Library/Developer/CommandLineTools' is a command line tools instance
```

No `xcodebuild`, no iOS SDK, no Simulator, no code signing. Installing Xcode is
a ~10–15 GB download plus a multi-GB install — heavy and slow, though not
irreversible.

**2. No rustup, so no iOS Rust std.** Same root cause as Android, verified the
same way:

```
$ cargo build --target aarch64-apple-ios
error[E0463]: can't find crate for `core`
  = help: consider downloading the target with `rustup target add aarch64-apple-ios`
```

### The C interface does not exist yet

This is the significant finding, and it is easy to assume otherwise.

**This repository exposes no C interface.** A search of the entire repo for
`extern "C"` and `#[no_mangle]` returns **zero** matches. The only FFI surface
is `pylib/rsbridge` — `crate-type = ["cdylib"]`, but a **PyO3 Python extension
module**, i.e. the Python ABI, not a callable C ABI. No `staticlib` target, no
cbindgen, no generated header anywhere.

The JNI bridge that AnkiDroid uses is not in this repo either — it lives in
`Anki-Android-Backend/rslib-bridge`, and its symbols are JNI-mangled
(`Java_net_ankiweb_rsdroid_NativeMethods_openBackend`), so they are not reusable
from Swift/Objective-C as-is.

So "run Anki's Rust backend on device through its C interface" requires
**writing that interface first.** The good news is that the target is small and
well-shaped: `rslib-bridge` is a thin wrapper over three operations —
`init_backend(&[u8])`, a `run_method` protobuf bytes-in/bytes-out call, and a
close/free. `anki::backend::init_backend` exists in this fork at
`rslib/src/backend/mod.rs:70`. A C-ABI equivalent is a **new crate**
(`crate-type = ["staticlib", "cdylib"]`) of roughly 150–250 lines: the same
three entry points, C-ABI instead of JNI, returning length-prefixed byte
buffers with an explicit free function.

Adding such a crate would be additive (a new directory plus a workspace member
line), which fits the "prefer new files over editing existing ones" constraint.

### Honest cost

| Item                                                          | Cost                                                                                                               |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| Install Xcode                                                 | ~10–15 GB download, hours; **needs explicit approval** — heavy change to a shared machine                          |
| Install rustup + `aarch64-apple-ios`, `aarch64-apple-ios-sim` | ~15 min                                                                                                            |
| Write the C-ABI shim crate                                    | ~150–250 lines; roughly a day including a round-trip test                                                          |
| Swift/SwiftUI app to actually drive it                        | **Days to weeks — and it does not exist as open source.** AnkiMobile is closed-source and is not a starting point. |
| Apple Developer account for on-device (not simulator)         | $99/yr, plus provisioning                                                                                          |

**Verdict: not viable on this machine as configured**, and the dominant cost is
not the Rust side at all — it is that there is no open-source iOS client to host
the backend. The Rust shim is the cheap part. Budget accordingly, and treat
"backend running in an iOS _simulator_ via a test harness" as a far cheaper
milestone than "Anki running on device".

---

## 6. Status summary

| Target                    | Status                                                    | Blockers                                                                                                                     |
| ------------------------- | --------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| **Desktop (macOS arm64)** | ✅ **Builds and launches.** Verified running.             | None. `just check` green except `check:clippy`, which needs rustup so the 1.92.0 pin applies.                                |
| **Android**               | ⚠️ Path fully mapped and version-exact; **not built here** | No JVM, no Android SDK/NDK 29.0.14206865, no rustup/Android Rust targets. All installable.                                   |
| **iOS**                   | ❌ **Not viable as configured**                           | No Xcode, no rustup/iOS targets, **and no C interface exists in this repo** — it must be written. No open-source iOS client. |

---

## 7. Reproducing on a clean machine

```bash
# 1. Prerequisites: Homebrew, git, and Rust (rustup STRONGLY preferred over
#    Homebrew rust, so rust-toolchain.toml's 1.92.0 pin is honoured and clippy
#    passes; rustup is also mandatory for any mobile cross-compilation).
brew install ninja just

# 2. Clone and build. ftl submodules, protoc, Node, uv and Python are all
#    fetched automatically — do not install them by hand.
git clone <this-fork> && cd speedrun
just build          # ~2m20s cold on Apple Silicon

# 3. Launch against a throwaway collection.
just run -b /tmp/ankibase
```

Expect `Starting Anki 26.05...` and a main window with a deck browser.

If you also intend to run `just check`, do these two things first — see
[§3](#just-check-status--three-environmental-gotchas-all-diagnosed):

```bash
export PATH="$PATH:$HOME/.cargo/bin"              # for minilints
git submodule update --init qt/installer/mac-template   # for installer tests
```

Run `just --list` for the full recipe set. Do not call `./ninja`, `./run` or
`tools/*` directly — see `CLAUDE.md`.

---

## 8. Known repo quirk: `AGENTS.md`

Upstream Anki ships `AGENTS.md` as a **symlink to `CLAUDE.md`**. Some tooling
(including `fm-ensure-agents-md.sh`) expects the opposite convention —
`AGENTS.md` as the real file with `CLAUDE.md` symlinked to it — and will refuse
with:

```
conflict: AGENTS.md is a symlink; expected AGENTS.md to be the real file
```

This was left as upstream has it **deliberately**. Flipping the symlink would
rename two upstream-tracked files and create a permanent merge liability against
upstream for no functional gain — both names resolve to the same file either
way. Durable project knowledge therefore goes in `CLAUDE.md`, which _is_
`AGENTS.md`.
