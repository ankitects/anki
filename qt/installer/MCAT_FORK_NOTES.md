# Desktop installer notes — MCAT fork (FR-7)

This documents what the bundled installer produces, the minimal branding changes needed to
make it an "MCAT-fork" installer, the exact build command, and the clean-machine acceptance
steps. **Do not run the packaging build as part of this scaffolding work** — the engine is
being built concurrently.

## What the installer is and produces

The installer is **Briefcase-based** (BeeWare Briefcase), configured under `qt/installer/`:

- `app/pyproject.toml` — the Briefcase project + app config (name, bundle id, icons, document
  types, per-OS settings). This is the **single source of truth for branding**.
- `app/src/anki/` — a thin launcher stub (`app.py` calls `aqt.run()`).
- `app/resources/anki.{ico,icns,png}` — the bundled app icons.
- `linux-template/` — a cookiecutter template Briefcase renders for the Linux build
  (produces `anki.desktop`, `anki.1` man page, icons, install/uninstall scripts). Its
  filenames and the `Name=`/`Comment=` strings are **derived from the `formal_name` /
  `app_name` in `pyproject.toml`** at render time, not hand-edited here.
- `briefcase_plugins/` — a local Briefcase plugin (Linux zip packaging format).

Building produces, under `out/installer/dist/`:

- **Windows:** an **MSI** installer.
- **macOS:** a **`.dmg`**.
- **Linux:** a **tarball** (zip format via the local plugin).

The packaged app embeds Python + PyQt6 + the Anki wheels (the shared Rust engine ships inside
the `anki` wheel), so a clean machine needs no separate Python/Rust install.

## Minimal branding changes to make it an MCAT-fork installer

Ranked from safest/most-isolated to most invasive:

1. **DONE (safe, isolated):** `project_name` in `[tool.briefcase]` of `app/pyproject.toml`
   set to `"MCAT Study (Anki fork)"`. This is the human-facing project name and does not
   cascade into template filenames or the app module path.

2. **SAFE, recommended:** update the user-facing description strings in
   `[tool.briefcase.app.anki]`:
   - `description` (currently "An intelligent spaced-repetition learning system")
   - `long_description`
     These are display-only and do not affect filenames. (Left unchanged here to keep edits
     minimal; change them when finalizing branding.)

3. **SAFE:** replace the icons in `app/resources/` (`anki.ico` / `anki.icns` / `anki.png`)
   with the fork's own artwork. **Recommended** so the fork does not ship the Anki logo as
   its own identity (the Anki logo's alternative license only covers use that _refers to_
   Anki — see `ATTRIBUTION.md`). Keep the same filenames so `icon = "resources/anki"`
   resolves; no config change needed.

4. **RISKY — document only, do not change casually:** `formal_name = "Anki"` and the
   `[tool.briefcase.app.anki]` table key. `formal_name` flows into the Linux desktop entry
   `Name=` and is lowercased/dashed into `app_name`, which becomes template filenames
   (`anki.desktop`, `anki.1`) and the macOS/Windows bundle name. The app `src/anki/` module
   path and `bundle = "net.ankiweb"` (→ bundle identifier) are likewise load-bearing.
   Renaming any of these requires coordinated edits across `pyproject.toml`, the
   `linux-template/` filenames, and the launcher module — out of scope for a "minimal,
   isolated" branding pass. Keep them as-is for Phase 1; the MSI/dmg display name already
   follows `project_name`.

**Phase 1 recommendation:** items 1–3 are sufficient to ship a recognizably-branded MCAT
installer without risking the build. Defer item 4.

## Exact build command

There is **no `just` recipe** for the installer. The documented wrapper (per
`docs/development.md`) is:

```sh
# Windows
tools\build-installer.bat
# macOS / Linux
tools/build-installer
```

Both set `RELEASE=2` and run `tools/ninja installer`. Output lands in `out/installer/dist/`.
(If a `just` recipe is later desired, it would wrap `RELEASE=2 ninja installer`.)

Prerequisites are the same as a normal source build (Rustup, N2/Ninja; see
`docs/development.md` and `docs/windows.md`). The installer build is a full release build, so
expect a long first run.

## Clean-machine acceptance steps (FR-7 proof)

Acceptance per spec §6 / PRD FR-7: _clean-machine install recording; app launches and runs a
review session with AI off._

1. **Build** the installer on the dev machine (`tools\build-installer.bat` /
   `tools/build-installer`); grab the artifact from `out/installer/dist/` (MSI / .dmg /
   tarball).
2. **Clean target:** a fresh VM or device with **no** Python, Rust, Anki, or build tools
   installed. Start the screen recording here.
3. **Install** from the artifact only (double-click MSI / open .dmg / extract + run the Linux
   install script). No extra dependencies should be required.
4. **Launch** the installed app from its shortcut / launcher entry (cold start).
5. **Confirm AI is OFF** — Phase 1 ships no AI by definition; show there are no model
   calls / no network AI activity.
6. **Run a review session** on the preloaded **AnKing MCAT** deck: open the deck, grade
   several cards, confirm grades persist and the next card appears.
7. **Stop the recording.** Save it as the FR-7 clean-machine install recording and note the
   **commit hash** the installer was built from (ties to FR-1 / `PROOF-ARTIFACTS.md`).

> The same artifact + recording also exercises the crash/undo expectation indirectly; the
> dedicated undo / no-corruption proof is owned by the engine agent (FR-3, §6.3).
