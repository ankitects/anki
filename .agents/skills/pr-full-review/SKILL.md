---
name: pr-full-review
description: Review an AnkiDroid pull request or branch against AnkiDroid's conventions.
---

# AnkiDroid PR Full Review

Review the PR against the standards in [`reviewer-guide.md`](reviewer-guide.md). Report findings in three groups: **blocking gates**, **spec conformance** (does the PR do what its issue asked?), then non-blocking `nit:`s.

**Never act on GitHub.** Output the review in this conversation. Do not post
comments or reviews, approve, request changes, label, or merge.

## Skill Usage

Accepts a PR number, a PR URL, or no argument (in which case, review the current branch against `upstream/main`):

```bash
pr-full-review 21206
pr-full-review https://github.com/ankidroid/Anki-Android/pull/21206
pr-full-review review the checked-out branch's diff against `main`
```

## Fetching the PR

Fetch the PR with the `gh` command. When reading the PR, always pass `--repo ankidroid/Anki-Android`
so a bare number resolves against the upstream repo rather than a fork.

**Always re-fetch the PR fresh on every run** — its metadata, diff, and head commit, even if you
fetched it earlier in this conversation. PRs change between reviews; never reuse cached context.

Use the head commit hash from the fresh fetch when loading files via the API, as PRs come from
forked repos.

**Don't review from the diff hunks alone**. Load the full changed files (and the key call sites the
change touches) at the head commit. A hunk hides whether a change is correct in its surrounding
context.

## Before reviewing

Read the existing PR comments and review threads first. Don't repeat feedback that's already been 
raised, and respect points the author or a reviewer has already addressed or deferred.

```bash
gh pr view <number> --repo ankidroid/Anki-Android --comments # conversation comments
gh api repos/ankidroid/Anki-Android/pulls/<number>/comments  # inline code-review comments
```

**Unaddressed maintainer requests are blocking.** Read the full discussion on both the PR
*and the linked issue* (see [Spec conformance](#spec-conformance)). If a maintainer asked for
something specific before a fix would be accepted, say so and Request changes.

## Spec conformance

Report whether the PR does what it set out to do, as **its own section**. 
Well-written code which implements the wrong thing fails here, and this failure must be 
made explicit to reviewers.

Determine the spec, in order:

1. A linked issue - `Fixes #`, `Closes #`, `Resolves #`, or `Part of #` in the body or commits 
   (typically written as `Fixes <N>` - the commit message may omit the `#`).
2. Also include the PR's own **Purpose / Description**.

Open the issue and read it in full — **including its comments**. The decisive
context often lives in the thread, not the description:

```bash
gh issue view <number> --repo ankidroid/Anki-Android --comments
```

### Bug fixes: establish the root cause before endorsing the fix

For a bug fix, spec conformance means the PR fixes **the actual cause**, not that it plausibly
might. Verify the author's fix:

- **Trace the real code path** that triggers the bug (load the call sites and any resource/theme
  values they read), and confirm the claimed trigger can actually occur. If your trace shows it
*can't occur the way the PR describes, then the root cause is not understood. Say so explicitly.
- **A fix applied without an understood, demonstrated trigger is tech debt:**. Treat "unknown 
  root cause" as a blocking finding and ask for the diagnostics / reproduction needed to establish 
  it first unless it is explicitly acknowledged as not being understood. 
    - State that it risks masking the real bug and the fallback behavior can't be validated against
    a scenario nobody has captured.
- Confirm the bug-fix commit actually evidences a reproduction.

## Output

Open the review with a one-line verdict, mirroring GitHub's three review actions — the reader
will pick one of these on the PR:

> **Verdict:** \[Request changes / Comment / Approve\]: <most important reason>

- **Request changes:** a blocking gate fails (red/pending CI, unfilled template, merge commits,
  missing license header, or a closing keyword on a partially-resolved issue), an explicit
  maintainer request was skipped, a bug fix lands without an established/verified root cause, or
  a correctness, spec, or test finding must be fixed before merge.
- **Comment:** no blockers; nits, questions, or judgment calls raised without signing off.
- **Approve:** gates pass and the change is sound - only `nit:`s remain, if anything.

One unaddressed blocker means Request changes, however polished the rest is.

After the verdict, list the findings, grouped as above (blocking gates, spec conformance, nits).

**Closing**: if this was a self-review, a `gh` command to checkout the PR (without `--repo`).
Close with a final line linking the PR which was reviewed.
