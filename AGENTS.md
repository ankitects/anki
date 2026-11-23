# Agent instructions

AnkiQuest adds XP, streaks, friends, leaderboards and moments to Anki.

These rules apply to every agent (Claude, Codex, others) and to people too, in every AnkiQuest repository. Everything above "This repository" is the same in all of them. The source is [AnkiQuest/AnkiQuest-Agents](https://github.com/AnkiQuest/AnkiQuest-Agents): `shared.md` for the shared part, `repos/<repository>.md` for each "This repository" section. Edit it there, never `AGENTS.md` in a repository; AnkiQuest-Sync opens a pull request in each repository whose `AGENTS.md` differs.

## Repositories

All under [github.com/AnkiQuest](https://github.com/AnkiQuest). Every default branch is `master`.

| Repository | Upstream | Purpose |
|---|---|---|
| AnkiQuest-Server | none | The server (Rust), its web pages (TypeScript), the API examples, the shared fixtures and the pose masters. |
| AnkiQuest-Desktop | none | The Anki desktop add-on (Python). |
| AnkiQuest-Android | ankidroid/Anki-Android | The Android app (Kotlin): AnkiDroid with AnkiQuest added, and AnkiQuest Lite for stock AnkiDroid users (coming). |
| AnkiQuest-Anki | ankitects/anki | Anki's core for the Android app: upstream plus RWKV and FSRS-7. |
| AnkiQuest-Android-Backend | ankidroid/Anki-Android-Backend | The app's Rust backend, built from AnkiQuest-Anki; published to Maven Central. |
| AnkiQuest-GarmAnki | sypianski/garmanki | Garmin watch app and its Android companion. |
| AnkiQuest-Android-MCP | ankimcp/ankidroid-mcp | MCP server on Android for AnkiDroid and AnkiQuest. |
| AnkiQuest-AnkiConnectAndroid | KamWithK/AnkiconnectAndroid | AnkiConnect on Android for AnkiDroid and AnkiQuest. |
| AnkiQuest-Dev | none | The development environment: dev shells, toolchain pins, shared workflows and label set, NixOS on WSL, NixOS and nix-darwin modules, Windows and dev container setup. |
| AnkiQuest-Hosting | none | The NixOS configuration of the host that serves ankiquest.org (private); a submodule of float3/nixos. |
| AnkiQuest-Sync | none | Scheduled jobs: fork sync, watched sources, `AGENTS.md` distribution. |
| AnkiQuest-Agents | none | The source of every `AGENTS.md`. |
| AnkiQuest.github.io | none | The art gallery (coming). |

## People

- hill (GitHub float3).
- Aldana (GitHub AldanaBRZ).
- The owners: hill and Aldana together. They decide and approve, with equal rights and responsibilities in the project.
- Claude: Claude Code agents.
- Astra: Aldana's Codex agent, which posts as AldanaBRZ.

Never call a person "the owner"; name them. "Code owner" describes how you treat the code, not who decides. "The owners" means hill and Aldana together. Name the person who approved something: "hill approved…", "Aldana approved…". An agent passing on an approval links the person's own words.

When a decision or action is left for hill, ask hill a question with the options, the recommended one first, instead of listing it as an open item. In chat, use the tool for questions with options, if there is one.

## Glossary

- Companion: a character (Aki, Ankilope, Bluma, Anki-tan).
- Pose: one picture of a companion. Its master is `<companion folder>/<pose>.png` in AnkiQuest-Art; every other copy (Android drawable, add-on art, share card) is an exported copy of that pose, made by AnkiQuest-Art's exporter and published in its releases. The goal is every pose drawn for every companion (see Companion art).
- Mood: today's streak state, computed by the shared rule in `tests/fixtures/streak_moods.json`. Each mood names the pose it shows; several moods may show the same pose (`frozen_1` and `thawing` both show `frozen`). That is a mapping, not a fallback.
- Moment: something that happened worth telling (milestone, level up, place, deck conquered), shown as a card.
- Share: send a moment, board place or deck to friends in AnkiQuest or to another app. Don't say "shareable".
- "Drawable" and "art" are only platform file names in code. In docs and discussion, say "the pose's exported copy".

## Code ownership

You are the code owner and long-term maintainer of this codebase. The design is yours to decide, unless hill or Aldana overrules it.

Act like it: consolidate duplicated code instead of copying it, fix what you find instead of only reporting it, and leave the code better than you found it. List every change, including small ones, in the PR description; reviewers read the diff against it.

Don't hold on to the past. Deprecate and remove old code, tools, docs and layouts instead of keeping shims, aliases or "moved to" stubs, and make breaking changes when they make the design clearer. Pull requests that remove code are especially welcome. Say in the PR what breaks and who has to act. Two limits still apply: released apps keep working, so old endpoints, fields and API examples stay until no supported client uses them; and pose masters are never deleted (see below).

## Bugs: test first

1. Write a regression test that reproduces the bug.
2. Run it and watch it fail for the expected reason.
3. Fix the bug.
4. Run it again and watch it pass, together with the rest of the suite.

Say in the PR that the test failed before the fix. Never skip, disable or loosen a test to get CI green.

## Languages

Write new code in Rust, or TypeScript for the web, when the platform allows it. Use other languages only where the host demands them: Kotlin for the Android app, Python for the Anki desktop add-on. Do not add new Python tools or scripts; when you change an existing one substantially, port it to Rust.

## Code

- Name values: extract recurring or meaningful values into constants or enums; values from a spec (HTTP status codes, for example) always get a constant. Keep obvious one-off values inline.
- Return early; avoid deep nesting.
- Always use braces, even for one-line `if`s.
- Short function names, under 30 characters.
- Enums instead of booleans for function parameters.
- Blank lines between logical blocks, and a short comment saying what a block does and why.
- Keep members private by default. Widening visibility to share code instead of duplicating it is welcome; no approval is needed. Say in the PR what was widened and why.
- Layer the code: low-level mechanics (HTTP, database, files, platform APIs) sit behind an abstraction, and each layer talks only to the one directly below it.
- Prefer targeted edits over rewriting whole files.
- Keep all state explicit. Model a lifecycle or mode as one enum or sealed type with named states and named transitions, not as several booleans, nullable fields and timestamps whose combination means something. Every state is visible in one place, impossible combinations can't be represented, and tests cover each transition.

## Git and pull requests

- Start each session with `git fetch` and work from the latest default branch.
- Never push to the default branch; open a pull request.
- Pull requests by hill or Claude need an approving review from Aldana or Astra (the AldanaBRZ account). Pull requests by Aldana or Astra need an approving review from hill or Claude (the float3 account). One approval from the other side is enough; merge only once it is given and every check is green on the final commit. hill may merge trivially correct changes (documentation, AGENTS.md, README, labels, search and replace) without that review. The "Update AGENTS.md from AnkiQuest-Agents" pull requests opened by AnkiQuest-Sync need no review: the rules were reviewed here, in AnkiQuest-Agents. Nor do its "Use AnkiQuest-Art <tag>" pull requests: the art was reviewed in AnkiQuest-Art. Both merge once every check is green.
- AnkiQuest-Server, AnkiQuest-Desktop and AnkiQuest-Android merge through the merge queue on `master`, with the same settings: merge method Merge, build concurrency 5, group size 1 to 5, wait 5 minutes for the minimum group size, "Only merge non-failing pull requests", status check timeout 360 minutes (merge-group runs take up to four hours), and "Require branches to be up to date before merging" off, since the queue tests every pull request on top of the latest `master` and the ones queued ahead of it. Review settings: one approval, stale approvals dismissed on push, conversations resolved before merging, merge commits only. A repository that adds a merge queue uses the same settings; a change to them is made in every such repository at once. Every workflow with a required check also runs on `merge_group`. A job that cannot run in a merge group is skipped there by an `if:` on the job, which counts as passing; a path or branch filter on the workflow would leave the check pending and stall the queue.
- Commit messages: subject in the imperative, capitalised, at most 50 characters (72 hard limit), no full stop; a blank line; a body wrapped at 72 characters that explains what and why, not how.
- Name every model that did the work, the way ankidroid/Anki-Android#23564 does. Each commit ends with one `Assisted-by: <model> <reasoning effort>` trailer per model that worked on it, such as `Assisted-by: Claude Opus 5.5` or `Assisted-by: GPT-6.2-Sol xhigh`; leave out the effort when the tool has none. A pull request description starts with a note listing every model that worked on any of its commits:

  ```
  > [!NOTE]
  > Assisted-by: Claude Opus 5.5
  > Assisted-by: GPT-6.2-Sol xhigh
  ```

  Issues, comments and reviews an agent writes end with the same `Assisted-by` lines. No generated footer (`_Generated by ..._`) and no tool name in the line. Don't edit old posts to match. Generated images name the model that drew them in the pull request and in their generation record. If the tool does not say which model it used, write that instead of guessing.
- Keep the maintainers' private context out of everything committed or posted: code, comments, docs, commit messages, pull requests, issues and reviews. No quotes from or accounts of chats with hill, Aldana or Astra, no attributions like "(hill, <date>)" or "hill decided on <date>", and no dates of decisions; state the rule or the reason itself. Nothing about anyone's local setup either: operating system, shell, paths, machine or host names, sandboxes, network limits, missing tools or emulators. Testing notes say what ran and what didn't ("Emulator tests: not run"), not why in terms of a machine. Dates that are part of the content stay: releases, licence years, changelogs.
- Never link to an agent session (such as `claude.ai/code/session_…`) in commits, pull requests, issues or comments.
- No emoji in commits, pull requests, issues or comments.
- Never commit secrets, tokens or personal data. Secrets live on the servers, outside the repositories.
- AnkiQuest-Agents is public, so every `repos/<repository>.md` is public, including the sections of private repositories such as AnkiQuest-Hosting. They say what the repository is and how to work in it, and never name private hosts, addresses, ports, user names, secret paths, cloud account or region details, or the internal network layout.

## Reviews

- Review nitpicky and critical, and think every change through, security above all. Raise every nitpick, and request changes as many times as it takes to get it right.
- When your own work is under review, address every nitpick before merging.
- Ask hill for advice on implementation details.

## Forks and wiring

```
ankitects/anki                  --pr-->  AnkiQuest/AnkiQuest-Anki master
                                             | git submodule
ankidroid/Anki-Android-Backend  --pr-->  AnkiQuest/AnkiQuest-Android-Backend master
                                             | Maven Central: org.ankiquest:anki-backend-android
ankidroid/Anki-Android          --pr-->  AnkiQuest/AnkiQuest-Android master  --HTTPS-->  AnkiQuest-Server
                                             | content provider, through org.ankiquest:api
sypianski/garmanki              --pr-->  AnkiQuest/AnkiQuest-GarmAnki master
ankimcp/ankidroid-mcp           --pr-->  AnkiQuest/AnkiQuest-Android-MCP master
KamWithK/AnkiconnectAndroid     --pr-->  AnkiQuest/AnkiQuest-AnkiConnectAndroid master
```

The full, generated map of every repository, upstream, package, submodule and shared workflow, with pinned versions and what is out of date, is <https://ankiquest.github.io/wiring.mmd> (Mermaid source) and <https://ankiquest.github.io/wiring.html> (rendered); read it when a change crosses repositories.

- **Every fork** has one branch of ours, `master`: upstream's default branch plus our changes, consolidated. Upstream comes in through the sync job's pull request, merged as a merge commit; never squash or rebase it. Work from `master` and open pull requests against it.
- **Core forks** (AnkiQuest-Anki, AnkiQuest-Android-Backend): AnkiQuest-Anki `master` is ankitects/anki `main` plus the RWKV engine and FSRS-7 carried from JSchoreels' and kuma3's forks. AnkiQuest-Android-Backend `master` is upstream plus kuma3's backend wiring, with its `anki` submodule on a commit of AnkiQuest-Anki `master`; it publishes `org.ankiquest:anki-backend-android`, `anki-backend-core` and `anki-android-backend-testing` to Maven Central on a `*-ankiquest.*` tag.
- **Third-party app forks** (AnkiQuest-GarmAnki, AnkiQuest-Android-MCP, AnkiQuest-AnkiConnectAndroid): `master` is our version, with CI, Nix and the AnkiQuest API library `org.ankiquest:api` (coming). The branches `ankidroid-provider-check` and `ankiquest-support` stay ready to propose upstream: upstream's style and languages, no AnkiQuest-only changes. Keep upstream's languages for upstream's code in every fork.
- **Sync job** (AnkiQuest-Sync, daily and on demand): opens one pull request per fork from upstream's default branch, and lists the conflicting files. The `watch` job opens an issue in the fork a watched source feeds when it moves past the reviewed commit or version: JSchoreels/anki, JSchoreels/fsrs-rs, open-spaced-repetition/fsrs-rs, kuma3's core, backend and app, and new upstream backend versions (`watched.json`). The `agents` job opens the `AGENTS.md` pull requests.
- **Shared Nix and CI.** Every repository has a `flake.nix` whose dev shell, pinned toolchain and formatter come from AnkiQuest-Dev, taken as an input; never pin a toolchain in a repository's own flake. Workflows used by more than one repository are reusable workflows in AnkiQuest-Dev (`.github/workflows/`), called with `uses: AnkiQuest/AnkiQuest-Dev/.github/workflows/<name>.yml@master`.
- **README banner and social preview.** Every repository with a README banner or social preview draws both with AnkiQuest-Dev's `ankiquest-banner` (`tools/banner`) and checks them with its reusable `banner.yml`, the same way: the same sizes, background, Roboto, flags and pixel check. A repository keeps only its inputs, in `docs/graphics/banner.toml`: the text, its screens and the command that records them, and the companion pose, read from its master in AnkiQuest-Server. Never commit a banner drawn another way.

## Licence

Every AnkiQuest repository is under the AGPL wherever its upstream allows it.

- Our code and every new repository are AGPL-3.0-or-later: a `LICENSE` with the AGPL text, and the license field set wherever a manifest has one (`Cargo.toml`, `package.json`, `pyproject.toml`). AnkiQuest-Server and AnkiQuest-Desktop are AGPL-3.0-or-later (AnkiQuest/AnkiQuest-Server#102, AnkiQuest/AnkiQuest-Desktop#407); so is the AnkiQuest code in AnkiQuest-Android (AnkiQuest/AnkiQuest-Android#447).
- In forks, upstream code keeps its licence and notices. Our additions are AGPL-3.0-or-later where upstream's licence allows combining them: GPL-3.0 and AGPL-3.0 do (GPLv3 section 13). With a permissive upstream (MIT, BSD, Apache-2.0), our files are AGPL-3.0-or-later and upstream's notices stay.
- Branches meant for upstream (`ankiquest-support`, `ankidroid-provider-check`, the `upstream/*` pull request branches) use upstream's licence.
- Exception: the API library that third-party apps link (`api/` in AnkiQuest-Android, published as `org.ankiquest:api`) stays LGPL-3.0-or-later, AnkiDroid's licence for it, so apps under any licence can use it. Our changes to it are LGPL-3.0-or-later too; never move AGPL code into it.
- Poses keep the licence of their masters: Anki-tan's are CC BY-SA 4.0 (`anki-tan/LICENSE` in AnkiQuest-Art, by Shigeyuki), the other companions' fall under AnkiQuest-Art's licence. Don't change the licensing of a pose; that is hill's decision.
- SPDX headers (`SPDX-License-Identifier: AGPL-3.0-or-later`) go in new files where the repository already uses them, such as AnkiQuest-Android. Don't add or change headers in upstream files in bulk.

## Desktop and Android parity

AnkiQuest has two clients: the Android app ([AnkiQuest/AnkiQuest-Android](https://github.com/AnkiQuest/AnkiQuest-Android)) and the Anki desktop add-on (`addon/` in [AnkiQuest/AnkiQuest-Desktop](https://github.com/AnkiQuest/AnkiQuest-Desktop)). A player should get the same AnkiQuest on both.

- A user-facing feature or behaviour change lands on both clients, unless the platform can't do it (home-screen widgets, for example). Do both in one go when you can. Otherwise open an issue in the other repository, labelled `parity`, and link it from the PR.
- Rules both clients compute (streak verdicts, heatmap, card labels, deck names) are defined once, as cases in `tests/fixtures/*.json` in [AnkiQuest/AnkiQuest-Server](https://github.com/AnkiQuest/AnkiQuest-Server/tree/master/tests/fixtures). Both clients test against the same cases. Change the fixture first, then both implementations.
- Use the same names, wording and order for the same thing on both clients.
- When you find a gap between the clients, open a `parity` issue for it.

## Companion art

Every picture of a companion is a pose, including those on moments, share cards, widgets, notifications and the launcher icon. The section and tool names keep "art"; the rules speak of poses, masters and exported copies.

### The matrix

The art is a matrix: companions by poses. The goal is a full matrix: every pose that exists for one companion exists for all of them, so no pose is unique to one companion. `poses.json` in AnkiQuest-Art lists every pose once, with each extension's fallback and the core poses' temporary fallbacks; every repository reads it from the Art release it pins.

- **Core**: what a companion needs to go live. The core moods (face, asleep, happy, streak, sad, hopeful, frozen, neglected), the moment and share-card pictures the apps show (places, level up, goal, achievement, challenge and the others the fixture lists) and the launcher icon moods.
- **Extensions**: variants and tiers of a core pose, each with a fallback `poses.json` names: frozen 2 and 3 after frozen, fire 2 and 3 after fire, abandoned and dead after neglected, and so on. A missing extension shows its fallback.
- A new companion goes live only with every core pose drawn. Ankilope, Bluma and Anki-tan stay live while their missing core poses are drawn; until then each shows the fallback `poses.json` gives it (`core_fallbacks` for core poses). Drawing them comes first, in AnkiQuest/AnkiQuest-Server#57 (poses) and #116 (icons and moment pictures).
- A PR that adds a pose to the list adds its master for every live companion; CI fails it otherwise. A mood that needs a new picture adds a pose this way; otherwise it maps to an existing pose.
- No placeholder pictures: no stand-in, recoloured or borrowed picture for a missing pose. Bluma's poses recoloured from Ankilope's are the last ones and go once hers are drawn.
- Framing: `face` and the launcher icons are headshots, showing the face; every other pose, core moods included, is a full-body picture. A headshot or bust drawn for any other pose doesn't count as its master.
- Every drawing still needed is recorded in an issue in AnkiQuest-Server (#57, #116), for Astra to draw and PR.

### Masters, archive and exported copies

- Masters live only in AnkiQuest-Art, at `<companion folder>/<pose>.png`. A picture several poses share is named after its main pose (core, then extension, then moment picture), and `manifest.json` names it for the other poses. No two pictures in AnkiQuest-Art have the same bytes; `art-check` fails on a copy, in the archive too.
- `archive/` in AnkiQuest-Art holds every picture that is not a current master: `archive/<companion>/` for masters that were replaced, redrawn or retired, and `archive/anki-tan-pack/` for Shigeyuki's source pack, next to its licence. Never delete a master or a picture that can't be regenerated: move it to the archive. The archive is never exported to a client.
- A picture stays outside the archive only while something uses it: it is the master of a pose of a live or planned companion, or an exporter, the server or the website reads it. Everything else moves to the archive.
- Every exported copy (share cards, add-on, Android, gallery) is made by `tools/art-export` in AnkiQuest-Art and published in an AnkiQuest-Art release (`art-<N>`), with the masters, `manifest.json` and `poses.json`. No other repository commits a picture of a companion, generates copies itself or takes AnkiQuest-Art as a submodule.
- Every repository that uses art pins one release in `ankiquest-art.json` (tag, Art commit, SHA-256 of each asset), downloads only the assets it needs and checks their SHA-256 before use; Nix builds read the hashes from that file. AnkiQuest-Sync updates the pin in every repository when AnkiQuest-Art publishes a release; never edit the file by hand.
- The gallery (AnkiQuest.github.io) shows the matrix of all companions and poses, a page per companion with what it is missing and why, the licence of every picture, and the archive in a section of its own.

## Writing

Write comments, commit messages, issues and PR descriptions with as few words as needed. No praise, no filler, no superlatives. Em dashes take no spaces around them.

Mannered prose substitutes metaphor and flourish for direct statement. Instead of "a parameter worth varying," the mannered writer produces "a dial worth turning." Instead of "this point still matters," they write "this point earns its keep." The phrases exist to display the writer, not to convey the idea, and readers can tell. That is why mannered prose irritates: it makes the reader work harder so the writer can perform. It is also imprecise. Metaphors drag in connotations the writer did not choose and cannot control. The fix is to say what you mean. When a literal phrase is available, use it.

## This repository

AnkiQuest-Anki is a fork of [ankitects/anki](https://github.com/ankitects/anki): Anki's core, built into the Android app through AnkiQuest-Android-Backend.

- **Branches.** `master` is ankitects/anki `main` plus our carried commits: the RWKV engine and scheduler, FSRS-7 with JSchoreels' fsrs-rs fork, their protobuf messages and strings. Work from `master` and open pull requests against it. Our protobuf fields take numbers after upstream's; when upstream takes a number we use, renumber ours in the upstream merge.
- **Upstream's instructions.** `CLAUDE.md` is upstream's, with `@AGENTS.md` added as its first line; its rules still apply to code in this project (`just` recipes, `just check` before finishing). Upstream's own `AGENTS.md` is a link to it; ours replaces that link on `master`.
- **Upstream merges** come from the sync job's pull request; merge with a merge commit, never rebase. The queue builder, the scheduler, search and `Cargo.toml` conflict most often. After merging, move the backend's submodule when the backend needs it (see AnkiQuest-Android-Backend).
- **Watched sources.** JSchoreels/anki, JSchoreels/fsrs-rs, open-spaced-repetition/fsrs-rs and Pedrubik2000/kuma3-anki-core are ported by hand. AnkiQuest-Sync opens an issue here when they move; after porting or skipping, set `reviewed` in AnkiQuest-Sync's `watched.json`.
- **RWKV models** are user-supplied files with no public licence. Never commit weights. Tests that need a model file run only when one is placed locally; CI runs without them.
- **Checks to run** before pushing, for what you changed: `cargo fmt --check`, `cargo clippy -p anki --lib --tests -- -D warnings`, `cargo test -p anki --lib`; `just check` for changes outside rslib. Android targets are checked only by the backend's build.
