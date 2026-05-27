# FlashAI — Claude Code Instructions

This directory contains a standalone browser flashcard app (`index.html`) and
a Python/tkinter desktop version (`flashai.py`). There is no build system —
changes are verified by inspection and manual testing.

## Self-review before finishing

**Before marking any task complete, always run a self code-review:**

```
/code-review
```

If `/code-review` is not available, perform the review manually by diffing the
branch and checking every changed file against this checklist:

### HTML / JavaScript (`index.html`)
- [ ] No synchronous operations that should be `async` (especially IndexedDB calls)
- [ ] Every `async` function caller either `await`s it or chains `.catch(console.error)`
- [ ] Blob object URLs created with `blobToURL()` are cleaned up via `clearBlobURLs()`
  before loading the next card — no URL leaks
- [ ] `escHtml()` used on every user-supplied string inserted into the DOM via
  `innerHTML`; never use raw string concatenation + innerHTML for untrusted data
- [ ] API key is never logged, included in error messages, or sent anywhere
  except `api.anthropic.com`
- [ ] Dark-mode CSS variables applied consistently — any new surface uses
  `var(--surface)` / `var(--text)` rather than hard-coded colours
- [ ] `localStorage` used only for JSON-serialisable card data and settings;
  binary blobs always go to IndexedDB
- [ ] New card fields added to `makeCard()` are also handled in:
  `renderReviewCard`, `deleteCard`, `addCardFromModal`, and the browse table

### Python (`flashai.py`)
- [ ] Database writes always followed by `self.con.commit()`
- [ ] Anything that touches the UI from a background thread uses
  `self.after(0, callback, ...)` — never update tkinter widgets directly
  from a non-main thread
- [ ] Images stored as raw `bytes` (BLOB); always passed through
  `blob_to_photoimage()` before display; references kept alive on `self`
  to prevent garbage-collection of `PhotoImage`
- [ ] `schedule_card()` operates on a `deepcopy` — never mutates in place
- [ ] `on_show()` is called on every tab switch and must be idempotent
  (safe to call multiple times without duplicating widgets)

### Setup scripts
- [ ] `setup.sh` is executable (`chmod +x`)
- [ ] `setup.bat` uses `!` variable expansion (`setlocal enabledelayedexpansion`)
- [ ] Both scripts exit non-zero on error before writing `run.sh` / `run.bat`

---

## Responding to Gemini Code Assist review comments

Gemini Code Assist posts automated review comments on pull requests as the
bot user **`gemini-code-assist[bot]`**. Treat these comments exactly like
human reviewer feedback — they appear in `<github-webhook-activity>` events
or can be fetched via `mcp__github__pull_request_read` with
`method: get_review_comments`.

### Decision tree for each Gemini comment

1. **Valid bug or correctness issue** → fix it, push, reply only if the fix
   diverges from what Gemini suggested (explain why).

2. **Style / simplification suggestion that agrees with our conventions** →
   apply it silently, no reply needed.

3. **Suggestion that conflicts with an intentional design decision** →
   reply concisely explaining the reason (e.g. "We intentionally avoid
   `innerHTML` here to prevent XSS — using `textContent` instead.").
   Do **not** apply the change.

4. **False positive / outdated / doesn't apply** → skip silently. If the
   same false positive recurs across reviews, add a brief inline comment
   so future reviewers understand the intent.

5. **Ambiguous — could be interpreted multiple ways** → ask the user via
   `AskUserQuestion` before acting.

### What Gemini commonly flags in this codebase

| Pattern | Gemini's concern | Our stance |
|---|---|---|
| `innerHTML` with `escHtml()` | XSS risk | Acceptable — `escHtml` sanitises all inputs; note this in reply if challenged |
| `anthropic-dangerous-allow-browser: true` header | Security warning | Intentional — documented; user supplies their own key |
| `check_same_thread=False` in SQLite | Thread safety | Intentional — writes always happen on main thread; reads are safe |
| Broad `except Exception` in API thread | Overly broad catch | Intentional — all errors tunnelled back to UI via `on_error` callback |
| Missing type annotations in Python | Type safety | Add them if Gemini flags it — they improve readability |
| `eval` / `JSON.parse` on API response | Injection risk | Our prompt constrains output to JSON; response is never executed |

### Workflow

```
# Check for new Gemini comments
mcp__github__pull_request_read method=get_review_comments owner=krMaynard repo=anki-fork pullNumber=1

# After pushing a fix
git add -p          # stage only the relevant hunk
git commit -m "fix: <description of what Gemini flagged>"
git push -u origin claude/generative-ai-integration-BbVAC
```

Do **not** resolve Gemini's review threads manually — GitHub will mark them
as resolved automatically once the relevant code changes are pushed.

---

## Coding conventions for this sub-project

### JavaScript
- Module-level state in plain `let` / `const` at the top of the `<script>` block
- Async functions for anything touching IndexedDB; sync functions for
  localStorage, DOM, and SRS maths
- `uid()` for all new IDs (random base-36 + timestamp suffix)
- All user-visible strings run through `escHtml()` before entering the DOM

### Python
- One class per tab (`ReviewTab`, `GenerateTab`, …) inheriting `BaseTab`
- `_build()` creates widgets once in `__init__`; `on_show()` refreshes data
- Colours from the `C` dict only — never hard-coded hex strings in widget code
- `flat_button()` / `surface_frame()` / `small_label()` helpers for
  consistent styling; add new helpers rather than repeating style kwargs
- Type hints on all public methods; optional on private (`_`) methods

### Commit messages
Use the conventional-commit prefix that best fits:
- `feat:` — new user-visible capability
- `fix:` — bug fix
- `chore:` — tooling, deps, setup scripts, docs
- `refactor:` — code restructuring with no behaviour change
- `style:` — formatting only
