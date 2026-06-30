# Attribution & Credits

This project (the MCAT study app) is an **independent fork of Anki**. It is **not** an
official Anki product and is not affiliated with or endorsed by Ankitects Pty Ltd.

## License

This fork is licensed under the **GNU Affero General Public License, version 3 or later
(AGPL-3.0-or-later)** — the same license as upstream Anki. The authoritative license text and
the upstream per-file license notices are in [`LICENSE`](LICENSE), which this fork preserves
unchanged.

## Credit to Anki (upstream)

This software is derived from **Anki**, copyright **Ankitects Pty Ltd and contributors**.

- Project: https://apps.ankiweb.net
- Source: https://github.com/ankitects/anki
- Contributor list: [`CONTRIBUTORS`](CONTRIBUTORS)

Anki's core engine (Rust), Python library, PyQt GUI, and web frontend are reused here under
the AGPL-3.0-or-later. Our changes are layered on top of that codebase and are released under
the same license.

> Note on Anki's logo: Anki's logo is copyright Alex Fraser and is AGPL3-licensed, with a
> limited alternative license that only applies when the logo is used **to refer to Anki**.
> Because this is an independent fork with its own identity, this fork should use its own
> branding/icon rather than the Anki logo. Replacing the bundled icons is tracked as a
> branding task (see `qt/installer/MCAT_FORK_NOTES.md`); until then any retained Anki marks
> remain the property of their owner and are used only to credit the upstream project.

## BSD-3-Clause and other non-AGPL portions

Per upstream's [`LICENSE`](LICENSE), some portions are under licenses other than AGPL-3.0:

- **Contributions by Anki users** marked in [`CONTRIBUTORS`](CONTRIBUTORS) — BSD-3-Clause.
- `pylib/statsbg.py` — CC BY 4.0.
- Anki's translations — a mix of BSD and public domain.
- `qt/.../mpv.py`, `qt/.../winpaths.py` — MIT.
- MathJax — Apache-2.0; jQuery / jQuery-UI / plot.js — MIT; protobuf.js — BSD-3-Clause.

Binary distributions also include Qt translation files (LGPL) and the Python, Rust, and
JavaScript libraries this code references, each under its own license.

This fork does **not** modify or contradict those notices; the upstream [`LICENSE`](LICENSE)
remains the source of truth.

## MCAT deck content — AnKing MCAT (community deck)

The preloaded MCAT flashcard content is based on the community-maintained **AnKing MCAT**
deck, the most widely used MCAT Anki deck. It is a community resource created and maintained
by the AnKing team and MCAT-student contributors.

- Source / project: https://www.theanking.com (AnKing) — MCAT deck.

The AnKing MCAT deck content is the intellectual property of its respective creators and
contributors and is included here for study purposes with credit. The deck's own
terms/license govern its content; this fork's AGPL-3.0-or-later license applies to the
**software**, not to third-party deck content. If the deck's distribution terms require
removal or different handling, that takes precedence over inclusion here.

> TODO (engine/content agent): confirm the exact AnKing MCAT deck version/source URL and any
> redistribution terms once the deck is finalized for preload (FR-2).

## "MCAT" trademark

"MCAT" and "Medical College Admission Test" are programs of the Association of American
Medical Colleges (AAMC). This is an independent study tool; it is not affiliated with,
authorized, or endorsed by the AAMC. AAMC names are used only to describe the exam this tool
helps students prepare for.
