# Agent instructions

AnkiQuest adds XP, streaks, friends, leaderboards and moments to Anki.

These rules apply to every agent (Claude, Codex, others) and to people too, in every AnkiQuest repository. Everything above "This repository" is the same in all of them. The source is [AnkiQuest/AnkiQuest-Agents](https://github.com/AnkiQuest/AnkiQuest-Agents): `shared.md` for the shared part, `repos/<repository>.md` for each "This repository" section. Edit it there, never `AGENTS.md` in a repository; AnkiQuest-Sync opens a pull request in each repository whose `AGENTS.md` differs.

## Repositories

All under [github.com/AnkiQuest](https://github.com/AnkiQuest). Every default branch is `master`.

| Repository | Upstream | Purpose |
|---|---|---|
| AnkiQuest-Server | none | The server (Rust), its web pages (TypeScript), the API examples, the shared fixtures and the pose masters. |
| AnkiQuest-Desktop | none | The Anki desktop add-on (Python). |
| AnkiQuest-Android | ankidroid/Anki-Android | The Android app (Kotlin): AnkiDroid with AnkiQuest added. |
| AnkiQuest-Anki | ankitects/anki | Anki's core for the Android app: upstream plus RWKV and FSRS-7. |
| AnkiQuest-Android-Backend | ankidroid/Anki-Android-Backend | The app's Rust backend, built from AnkiQuest-Anki; published to Maven Central. |
| AnkiQuest-GarmAnki | sypianski/garmanki | Garmin watch app and its Android companion. |
| AnkiQuest-Android-MCP | ankimcp/ankidroid-mcp | MCP server on Android for AnkiDroid and AnkiQuest. |
| AnkiQuest-AnkiConnectAndroid | KamWithK/AnkiconnectAndroid | AnkiConnect on Android for AnkiDroid and AnkiQuest. |
| AnkiQuest-Dev | none | The development environment: dev shells, toolchain pins, shared workflows and label set, NixOS on WSL, NixOS and nix-darwin modules, Windows and dev container setup. |
| AnkiQuest-Hosting | none | The server hosts' NixOS configuration. |
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
- Pose: one picture of a companion. Its master is `static/<companion>/<pose>.png` in AnkiQuest-Server; every other copy (Android drawable, add-on art, share card) is an exported copy of that pose, generated and hash-checked. A pose exists only when it is drawn for every live companion.
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
- Pull requests by hill or Claude need an approving review from Aldana or Astra (the AldanaBRZ account). Pull requests by Aldana or Astra need an approving review from hill or Claude (the float3 account). One approval from the other side is enough; merge only once it is given and every check is green on the final commit. hill may merge trivially correct changes (documentation, AGENTS.md, README, labels, search and replace) without that review. The "Update AGENTS.md from AnkiQuest-Agents" pull requests opened by AnkiQuest-Sync need no review: the rules were reviewed here, in AnkiQuest-Agents (hill, 2026-11-20).
- Commit messages: subject in the imperative, capitalised, at most 50 characters (72 hard limit), no full stop; a blank line; a body wrapped at 72 characters that explains what and why, not how.
- Name every model that did the work, the way ankidroid/Anki-Android#23564 does (hill, 2026-11-20). Each commit ends with one `Assisted-by: <model> <reasoning effort>` trailer per model that worked on it, such as `Assisted-by: Claude Opus 5.5` or `Assisted-by: GPT-6.2-Sol xhigh`; leave out the effort when the tool has none. A pull request description starts with a note listing every model that worked on any of its commits:

  ```
  > [!NOTE]
  > Assisted-by: Claude Opus 5.5
  > Assisted-by: GPT-6.2-Sol xhigh
  ```

  Issues, comments and reviews an agent writes end with the same `Assisted-by` lines. No generated footer (`_Generated by ..._`) and no tool name in the line. Don't edit old posts to match. Generated images name the model that drew them in the pull request and in their generation record. If the tool does not say which model it used, write that instead of guessing.
- Never link to an agent session (such as `claude.ai/code/session_…`) in commits, pull requests, issues or comments.
- No emoji in commits, pull requests, issues or comments.
- Never commit secrets, tokens or personal data. Secrets live on the servers, outside the repositories.

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

- **Every fork** has one branch of ours, `master`: upstream's default branch plus our changes, consolidated. Upstream comes in through the sync job's pull request, merged as a merge commit; never squash or rebase it. Work from `master` and open pull requests against it.
- **Core forks** (AnkiQuest-Anki, AnkiQuest-Android-Backend): AnkiQuest-Anki `master` is ankitects/anki `main` plus the RWKV engine and FSRS-7 carried from JSchoreels' and kuma3's forks. AnkiQuest-Android-Backend `master` is upstream plus kuma3's backend wiring, with its `anki` submodule on a commit of AnkiQuest-Anki `master`; it publishes `org.ankiquest:anki-backend-android`, `anki-backend-core` and `anki-android-backend-testing` to Maven Central on a `*-ankiquest.*` tag.
- **Third-party app forks** (AnkiQuest-GarmAnki, AnkiQuest-Android-MCP, AnkiQuest-AnkiConnectAndroid): `master` is our version, with CI, Nix and the AnkiQuest API library `org.ankiquest:api` (coming). The branches `ankidroid-provider-check` and `ankiquest-support` stay ready to propose upstream: upstream's style and languages, no AnkiQuest-only changes. Keep upstream's languages for upstream's code in every fork.
- **Sync job** (AnkiQuest-Sync, daily and on demand): opens one pull request per fork from upstream's default branch, and lists the conflicting files. The `watch` job opens an issue in the fork a watched source feeds when it moves past the reviewed commit or version: JSchoreels/anki, JSchoreels/fsrs-rs, open-spaced-repetition/fsrs-rs, kuma3's core, backend and app, and new upstream backend versions (`watched.json`). The `agents` job opens the `AGENTS.md` pull requests.
- **Shared Nix and CI.** Every repository has a `flake.nix` whose dev shell, pinned toolchain and formatter come from AnkiQuest-Dev, taken as an input; never pin a toolchain in a repository's own flake. Workflows used by more than one repository are reusable workflows in AnkiQuest-Dev (`.github/workflows/`), called with `uses: AnkiQuest/AnkiQuest-Dev/.github/workflows/<name>.yml@master`.

## Licence

Every AnkiQuest repository is under the AGPL wherever its upstream allows it (hill, 2026-11-20).

- Our code and every new repository are AGPL-3.0-or-later: a `LICENSE` with the AGPL text, and the license field set wherever a manifest has one (`Cargo.toml`, `package.json`, `pyproject.toml`). AnkiQuest-Server, AnkiQuest-Desktop and the AnkiQuest code in AnkiQuest-Android are AGPL-3.0-only today; they keep that, in new files too, until hill decides in AnkiQuest/AnkiQuest-Server#95.
- In forks, upstream code keeps its licence and notices. Our additions are AGPL-3.0-or-later where upstream's licence allows combining them: GPL-3.0 and AGPL-3.0 do (GPLv3 section 13). With a permissive upstream (MIT, BSD, Apache-2.0), our files are AGPL-3.0-or-later and upstream's notices stay.
- Branches meant for upstream (`ankiquest-support`, `ankidroid-provider-check`, the `upstream/*` pull request branches) use upstream's licence.
- Exception: the API library that third-party apps link (`api/` in AnkiQuest-Android, published as `org.ankiquest:api`) stays LGPL-3.0-or-later, AnkiDroid's licence for it, so apps under any licence can use it. Our changes to it are LGPL-3.0-or-later too; never move AGPL code into it.
- Poses keep the licence of their masters: Anki-tan's are CC BY-SA 4.0 (`static/anki-tan/LICENSE` in AnkiQuest-Server, by Shigeyuki), the other companions' fall under AnkiQuest-Server's licence. Don't change the licensing of a pose; that is hill's decision.
- SPDX headers (`SPDX-License-Identifier: AGPL-3.0-or-later`) go in new files where the repository already uses them, such as AnkiQuest-Android. Don't add or change headers in upstream files in bulk.

## Desktop and Android parity

AnkiQuest has two clients: the Android app ([AnkiQuest/AnkiQuest-Android](https://github.com/AnkiQuest/AnkiQuest-Android)) and the Anki desktop add-on (`addon/` in [AnkiQuest/AnkiQuest-Desktop](https://github.com/AnkiQuest/AnkiQuest-Desktop)). A player should get the same AnkiQuest on both.

- A user-facing feature or behaviour change lands on both clients, unless the platform can't do it (home-screen widgets, for example). Do both in one go when you can. Otherwise open an issue in the other repository, labelled `parity`, and link it from the PR.
- Rules both clients compute (streak verdicts, heatmap, card labels, deck names) are defined once, as cases in `tests/fixtures/*.json` in [AnkiQuest/AnkiQuest-Server](https://github.com/AnkiQuest/AnkiQuest-Server/tree/master/tests/fixtures). Both clients test against the same cases. Change the fixture first, then both implementations.
- Use the same names, wording and order for the same thing on both clients.
- When you find a gap between the clients, open a `parity` issue for it.

## Companion art

Every picture of a companion is a pose, including those on moments, share cards, widgets and notifications. The section and tool names keep "art"; the rules speak of poses, masters and exported copies.

- Masters live only in `static/<companion>/` in AnkiQuest-Server. Every exported copy (share cards, add-on, Android, docs) is generated from its master by the exporters and hash-checked in CI; never commit a hand-made copy.
- Never delete a master, even when nothing uses it any more: `static/<companion>/` is the only place that keeps old and retired art, so it can be reused or merged later. Exported copies are generated: overwrite them, and delete a copy that was renamed, retired or is no longer used, together with its references. Don't keep unused copies in the clients.
- Every live companion has every pose listed in [`tests/fixtures/companion_poses.json`](https://github.com/AnkiQuest/AnkiQuest-Server/blob/master/tests/fixtures/companion_poses.json) in AnkiQuest-Server; both clients use that list.
- A new companion goes live only with every pose drawn.
- A PR that adds a pose to the list adds its master for every live companion. CI fails a PR that adds a pose without them. A mood that needs a new picture adds a pose this way; otherwise it maps to an existing pose.
- No placeholder pictures: no stand-in, recoloured or borrowed picture for a missing pose.
- One exception (hill, 2026-11-19): Anki-tan goes live before her missing extensions are drawn (`streak_hot`, `streak_blazing`, `frozen_2`, `frozen_3`, `abandoned`, `leech_defeated`, `winner`); they show their fallbacks until then. Drawing them comes first in AnkiQuest/AnkiQuest-Server#57. No other companion goes live with missing poses.
- Until the missing poses are drawn (AnkiQuest/AnkiQuest-Server#57), the clients keep the current fallbacks: a missing extension shows the fallback the list gives it (a streak tier shows streak, frozen 3 shows frozen), and Bluma shows Ankilope's poses recoloured where hers are missing. These are the only poses not drawn for every live companion, and they are temporary. Once every live companion has every pose, remove the fallbacks from the list and both clients.

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
