# Proof Artifacts checklist — Wednesday (Phase 1)

> Tracks every proof the spec (§6 "Due Wednesday" + §9) and PRD (§9) require, so nothing is
> lost. Update **Status** as artifacts land. Statuses: `TODO` / `IN PROGRESS` / `DONE`.

| #  | Proof artifact                                                                       | Spec / PRD ref      | Owner                                   | Status      | Location / link                                                 |
| -- | ------------------------------------------------------------------------------------ | ------------------- | --------------------------------------- | ----------- | --------------------------------------------------------------- |
| 1  | **Commit hash** of the forked, building checkout                                     | FR-1, §9            | engine / orchestrator                   | TODO        | `[[commit hash]]`                                               |
| 2  | **Clean-build recording** — fresh checkout builds with documented steps (`just run`) | FR-1, §6            | engine / orchestrator                   | TODO        | `[[recording link]]`                                            |
| 3  | **Rust-change diff**                                                                 | FR-3, §6.1, §7a     | engine                                  | TODO        | `[[diff / PR link]]`                                            |
| 4  | **≥3 Rust unit tests** passing (for the new queue / NTR / mastery query)             | FR-3, §6.2, §7a     | engine                                  | TODO        | `[[test names + results]]`                                      |
| 5  | **1 Python test** that calls the new RPC from Python                                 | FR-3, §6.2, §7a     | engine                                  | TODO        | `[[test name + result]]`                                        |
| 6  | **Undo works + no collection corruption** proof (crash/undo check)                   | FR-3, §6.3, §7a/§7g | engine                                  | TODO        | `[[recording / log]]`                                           |
| 7  | **One-page note**: why the change belongs in Rust, not Python                        | §6.4, §7a           | engine fills template                   | IN PROGRESS | `speedrun_plans/rust-change-note.md` (template ready)           |
| 8  | **List of upstream files touched** + merge difficulty                                | §6.5, §7a           | engine fills template                   | IN PROGRESS | `speedrun_plans/files-touched.md` (template ready)              |
| 9  | **Review-session recording** on the MCAT deck (grades persist, update NTR/DSR)       | FR-4, §6            | engine / product                        | TODO        | `[[recording link]]`                                            |
| 10 | **Memory score with range + give-up rule** visible in the app                        | FR-5, §6            | product                                 | TODO        | `[[screenshot / recording]]`                                    |
| 11 | **Coverage map** visible (drives give-up rule)                                       | FR-5, §7c           | product                                 | TODO        | `[[screenshot]]`                                                |
| 12 | **Clean-machine desktop install recording** (install → launch → review, AI off)      | FR-7, §6            | installer / this agent documented steps | TODO        | steps: `qt/installer/MCAT_FORK_NOTES.md`; recording: `[[link]]` |
| 13 | **Phone review-session recording** on the shared Rust engine (AnkiDroid-based)       | FR-8, §6            | mobile                                  | TODO        | `[[recording link]]`                                            |

## Notes

- **No AI in any Wednesday artifact** — no model calls, no generated cards, no chatbot
  (spec §6). Recordings should make "AI off" evident.
- Artifacts 7 and 8 are scaffolded templates in `speedrun_plans/`; engine agent supplies the
  specifics.
- Artifact 12's documented procedure lives in `qt/installer/MCAT_FORK_NOTES.md`
  (build command + clean-machine acceptance steps); only the recording itself is outstanding.
- Two-way sync is **not** a Wednesday proof (Friday item) — intentionally absent here.
- The README's build instructions back artifacts 1–2; verify the documented `just run` steps
  reproduce on a clean checkout before recording.

---

_Placeholders remaining: commit hash and all recording/diff/test/screenshot links — filled as
each artifact is produced._
