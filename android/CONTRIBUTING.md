> [!TIP]
> This page summarizes our [Development Guide](https://github.com/ankidroid/Anki-Android/wiki/Development-Guide). 
> If anything seems unclear, please search the guide 

> [!IMPORTANT]
> This guide is for developers. See [non-developer contributions guide](https://github.com/ankidroid/Anki-Android/wiki/Contributing) for other ways to help.

We want to help! This process helps us keep the repository moving, and aims to reduce the amount of 
 time you're waiting for an answer from us. **Please** reach out if things are unclear or causing 
 you slowdowns when contributing and we'll do our best to improve the process. Much of this process
 is situational, and we can help/advise you on skipping steps while you become familiar with the code.

## Joining our community

It is recommended, but optional to join our [Discord](https://discord.gg/xeb7bBZVJ6) and introduce yourself:
* [#ankidroid-dev](https://discord.gg/xeb7bBZVJ6) - introduce yourself and say hi
* OR: [#ankidroid-gsoc](https://discord.com/channels/368267295601983490/819288899302064188) - (if you're here for Google Summer of Code)
  * Our [GSoC guide](https://github.com/ankidroid/Anki-Android/wiki/Google-Summer-of-Code) will also be useful  

Discord is great for real-time/casual conversations. Records of the outcomes of development-related 
discussions should be copied to the relevant PR/issue (ideally linking to the Discord message).
 It's not expected that Discord history will be permanently retained.

## Google Summer of Code (GSoC)

GSoC contributors are expected to operate with a high degree of independence. Please read our 
[GSoC Wiki](https://github.com/ankidroid/Anki-Android/wiki/Google-Summer-of-Code) for more contribution instructions. Mentors are available to
provide guidance, feedback, and review rather than step-by-step instructions. Before asking questions, you are expected to read relevant
documentation, search existing issues/PRs, and make a concrete attempt to understand the problem.

We receive a high volume of GSoC-related questions during the application period. To keep the project
sustainable, general questions should be asked in public channels [#ankidroid-dev](https://discord.gg/xeb7bBZVJ6) and be
clear, specific, and actionable. Repeated questions, direct messages to mentors, or requests for
individual evaluation or proposal feedback may be ignored. Your ability to communicate clearly and work independently is an important part of how proposals are evaluated.

## AI Policy

> [!CAUTION]
> Using AI tools as a new contributor will result in moderator action.

New contributors **may not** use any AI tools to produce contributions for AnkiDroid. This includes 
 code, documentation, and GitHub comments (including spelling and grammar corrections).

See our [AI policy](AI_POLICY.md) for when these restrictions are lifted from your account, and why 
 they exist.

## Selecting an issue [[GitHub Search](https://github.com/ankidroid/Anki-Android/issues?q=is%3Aissue%20state%3Aopen)]

If you are starting out with open source, we recommend starting with: [#13282: Fix Android Studio Warnings](<https://github.com/ankidroid/Anki-Android/issues/13282>).
This is intended to produce a small, mergeable Pull Request (PR) which allows you to familiarize yourself with AnkiDroid development and getting the project building.

We list open issues on [GitHub](https://github.com/ankidroid/Anki-Android/issues?q=is%3Aissue%20state%3Aopen). Here is a list of useful queries for selecting an issue:

| **Purpose**                              | **Query**                   | **Comments**                                                                                                                                                                                      |
|------------------------------------------|-----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Is a 'Good First Issue'                  | `label:"Good First Issue!"` | These issues should be well-documented and workable, but are often assigned and completed quickly.                                                                                                |
| Is not assigned to a user                | `no:assignee`               | Two people working on an issue leads to wasted effort<br/>Issues are commonly assigned, but not worked on. If it's been a couple of weeks, feel free to comment asking if it's still in progress. |
| Has no associated PR                     | `-linked:pr`                | Two people working on an issue leads to wasted effort<br/>If a linked PR is closed, it is likely that the issue will be open for contributions.                                                   |
| Has not been updated in the last 30 days | `updated:<@today-30d`       | If the issue is assigned/has a linked PR, ping for a status update if you'd want to work on the issue                                                                                             |

[#13283: Add tests to the code](<https://github.com/ankidroid/Anki-Android/issues/13283>) is also an
 extremely useful place to start.

Once you have found an issue, please make a comment asking for it to be assigned to you. 
If the issue is unassigned, assume it is workable and begin work.

## Making progress

All issues are different. Use your judgment to decide if these steps are required. 

1. Reproduce the issue. Comment with the version number/debug info if reproduced the issue and
   were unsure whether it was current. If you can't reproduce it:
   * It may be fixed, download [the release](https://github.com/ankidroid/Anki-Android/releases) 
    associated with the issue to check you reproduced it correctly, then comment
   * `@mention` the submitter for more information
2. Documentation: As you are working on an issue, feel free to comment with relevant progress you have made. 
     Be brief, and only post if information is relevant to someone else continuing to work on the 
     issue. For example:
    * Relevant files to the issue
    * Relevant developer documentation
    * Understanding of how the issue occurred (example: logcat output)
3. Propose solution(s)
   * If you believe there are multiple reasonable solutions to the issue, post a comment documenting
    them, feel free to link the proposal on Discord

When posting comments, aim to be clear and brief. Use `<details>` if posting large images, or blocks
 of text.

## Before writing code (one-time)

> [!IMPORTANT]
> `git clone` your fork and define `origin` and `upstream`: [Wiki - Repository Setup](https://github.com/ankidroid/Anki-Android/wiki/Development-Guide/#initial-setup-one-time) 

> [!TIP]
> Ensure that `user.email` is associated with your GitHub account, so your commits appear on your heatmap  
> https://docs.github.com/en/account-and-profile/how-tos/email-preferences/setting-your-commit-email-address

> [!TIP] 
> Keep `main` on your fork in sync with `upstream/main` when starting new work
> If you make a commit to `main`, make a new branch to retain the commit, then `git rebase` and drop
> your commit from `main`.

Ensure you branched off `main` recently and are submitting from a branch on your fork (not `main`). 

```sh
git checkout main
git pull upstream main
git checkout -b my-feature-branch
# make your changes to the source code
git push origin HEAD
```

See also: [Wiki - Git - One time setup guide](https://github.com/ankidroid/Anki-Android/wiki/Development-Guide/#initial-setup-one-time)

### License/copyright headers

Files you create MUST either:
1. Start with a [SPDX License ID](https://spdx.dev/learn/handling-license-info/): `// SPDX-License-Identifier: GPL-3.0-or-later`
   - Recommended for most files
   - Use `LGPL-3.0-or-later` for the API
2. Start with a [long-form license](https://softwarefreedom.org/resources/2007/gpl-non-gpl-collaboration.html#x1-40002.2) including copyright.  
   - If the license is non-GPL
   - Pre-2026 mono-licensed GPL files may use this form, it is not recommended for new mono-licensed GPL files.
3. Follow the [`REUSE.toml` spec](https://reuse.software/spec-3.2/#reusetoml)
   - For new files where the above is not possible/desired (images etc...).

#### Copyright headers

Copyright headers are **optional** and must come after the license identifier line. 
You own the copyright of your contributions, regardless of the copyright header.

See [docs/contributing/copyright-headers.md](docs/contributing/copyright-headers.md) for how to 
 apply a copyright header. 

## Before submitting a Pull Request (PR)

> [!WARNING]
> Submitting a PR from `main` makes it difficult to submit multiple pull requests and makes it
> difficult for your fork of `main` to stay in sync with `upstream`

We have a limited amount of time to spend on the project. We are trying our best.
 A PR which is well-formed saves us time, and will be merged more quickly.
 Unless requested, PRs should be sent in a form where you feel they should immediately be merged.
  
1. Please run these checks before submitting a PR: [workflows/README.md](https://github.com/ankidroid/Anki-Android/tree/main/.github/workflows/README.md#quality-checks). Your PR will not be reviewed until automated checks 
   using GitHub Actions pass.
2. Check commits/diffs
    * Each commit should compile, and have one purpose
        * Rebase and add additional commits if a change contains multiple purposes
    * No merge commits in the history
    * The 'Files Changed' tab of the PR should only contain relevant changes
        * Incidental changes (lint, refactorings) should be in separate commits
        * Whitespace changes should be reverted  
3. Write a PR description
    * Our [PR template]((https://github.com/david-allison/Anki-Android/blob/contributing-md/.github/pull_request_template.md)) **must** be filled (unless trivial). Create PRs using GitHub so this template will be present in the PR Description area, ready for you to fill in with information about your work.

Writing automated tests helps reviewers be more confident that your change is correct. This is currently 
 optional but recommended, and should reduce the number of rounds of reviews.
 See also: [Wiki - Testing guide](https://github.com/ankidroid/Anki-Android/wiki/Testing).

## Handling requests for changes

> [!IMPORTANT]
> Don't close a PR if something unexpected happened, fix it locally via use of `git rebase`, then push your fix up to github to fix the PR by using `git push --force` to fix it, or get in touch.

AnkiDroid expects use of `git rebase` and `git push --force` to PR branches. PRs with merge commits 
 will not be merged.

Any mistakes made in commits should be rebased out of the history before merge.

See: https://www.atlassian.com/git/tutorials/rewriting-history/git-rebase

### Comments/Suggestions/Patches

Most changes are requested via **comments** on a Pull Request.
 You do not need to write a reply to all comments, a +1 reaction that the comment is handled 
 is sufficient.
If a comment is confusing, please ask for clarification: you should aim to understand the 'why' of a
 review comment, rather than following the comment without thinking about the impact.

If a **suggestion** is provided, feel free to apply it in the GitHub UI. Then ideally you pull those new commits down via`git rebase` or `git pull` and then you `squash` the changes into the relevant commits 
 locally and `force push` the branch to keep the history clean

Sometimes a reviewer takes the time to provide a **patch**. These should be considered high value as it is a large time investment on the part of the reviewer. If you agree with the changes proposed via the patch, copy it and apply it locally. See [Wiki: Applying Patches](https://github.com/ankidroid/Anki-Android/wiki/Development-Guide/#applying-a-patch) 
 on how to apply a patch

### Status

We use labels to determine which PRs are to be reviewed.

If you feel your PR has the wrong status, after 24 hours please `@mention` one of:
  * A past reviewer
  * A maintainer who commented on the issue
  * `@AnkiDroid Reviewer` on our [Discord](https://discord.gg/qjzcRTx)

If you feel your PR is no longer reviewable because it requires significant work to pass CI or incorporate suggested changes, please [mark it as a draft](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/changing-the-stage-of-a-pull-request#converting-a-pull-request-to-a-draft) until changes are made.

#### Label guide

* **No label**
  * PRs should not be unlabeled, please ping a maintainer if this occurs. 
* **[Needs Author Reply](https://github.com/ankidroid/Anki-Android/labels/Needs%20Author%20Reply)**
  * The PR will unlikely be looked at until requested changes are made.
* **[Has Conflicts](https://github.com/ankidroid/Anki-Android/labels/Has%20Conflicts)**
  * The Pull Request needs to be rebased onto `upstream/main`, conflicts should be fixed
  then the commits should be force pushed 
* **[Needs Review](https://github.com/ankidroid/Anki-Android/labels/Needs%20Review)**
  * Awaiting review from a reviewer
* **[Needs Second Approval](https://github.com/ankidroid/Anki-Android/labels/Needs%20Second%20Approval)**
  * Awaiting a review from another reviewer
* **[Pending Merge](https://github.com/ankidroid/Anki-Android/labels/Pending%20Merge)**
  * No action is needed. Awaiting potential additional action, then a marge by a maintainer.
* **[Blocked by dependency](https://github.com/ankidroid/Anki-Android/labels/Blocked%20by%20dependency)**
  * The PR will not be merged until another issue/PR is merged. Please ping if you don't understand 
    why this occurs. 
* **[Strings](https://github.com/ankidroid/Anki-Android/labels/Strings)**
  * No action is needed. Maintainers need to perform extra steps once the PR is 'Pending Merge'. 
* **[squash-merge](https://github.com/ankidroid/Anki-Android/labels/squash-merge)**
  * If you squash merge your PR, it will be mergeable, otherwise a maintainer will squash merge it 
    when it moves to the 'Pending Merge' state

## After a PR is waiting for reviewer feedback

Feel free to select a new issue but _but do focus on resolving review requests on your previous PRs 
 to get those merged as fast as possible_. Once you've done a handful of '**Good First Issues**', 
 we hope you feel that most of the issues in the repo are within your grasp.

Good First Issues take time to write, we don't want experienced contributors taking on too many, 
 as there are likely higher impact issues to resolve, and it makes it harder for new contributors 
 to find something to get started with

When submitting a new PR, ensure that you're branching from the latest
 commit on `main`, and `drop` any accidental commits to main which you made 