## AnkiDroid AI Tool Use Policy

We expect that contributors will provide a net benefit to AnkiDroid over time, in return we offer 
 mentorship and guidance.

AI generated contributions are nullifying our mentorship efforts by consuming guidance meant for 
 humans and shifting the responsibility of ensuring correctness onto our code reviewers.

This policy aims to:

* Ensure contributors can contribute without AI assistance.
* Provide reviewers with a framework to block contributions which do not benefit AnkiDroid.
* Ensure mentors are mentoring people, not AI output.

### New Contributors

Before you have 3 merged pull requests, you may **not** use any AI tools to produce contributions 
 for AnkiDroid.
This includes code, documentation, and GitHub comments (including spelling and grammar corrections).

If a reviewer suspects that **any** part of a contribution is produced with AI tools, a warning will
 be issued. Pull requests may be closed at the reviewer's discretion.

You may be banned from the repository if this occurs a second time.

### Contributors

* You **must** be able to explain all your contributions.
* When acting as a reviewer, you **may** use AI tools to produce code review comments. Prefer a
  summary, with the exact AI-generated wording in a `<details>` block.
* AI tools **should not** be used to produce other GitHub comments, including filling in the pull
  request template.
* Contributions that make nontrivial use of AI tools **must** be labelled, with the tool version.

#### Exceptions

AI-generated issues may be submitted at a maintainer's discretion.

### Handling Questions about AI Use

If you are not sure whether a tool or workflow counts as AI usage under this policy, please ask
**before** using it. Ask your question in a public [#ankidroid-dev](https://discord.gg/xeb7bBZVJ6) so that the answer
helps everyone. Direct messages to mentors/reviewers about AI usage may not get a reply.

If a reviewer raises concerns about AI usage in your contribution, reply clearly and honestly with
facts. Please do not argue about the policy in PRs. Repeated arguments, hiding AI usage,
or trying to bypass this policy may lead to moderator action.

### Disclosure

Disclose which AI tools were used.

**Commit messages** **must** use the `Assisted-by:`
 [git trailer](https://git-scm.com/docs/git-interpret-trailers#_description).

```
docs: example title

[Optional commit description]

Assisted-by: GPT-6-Astra xhigh
Assisted-by: Claude Opus 5.5
```

**GitHub comments** **must** disclose AI tool use, typically using one of the following formats:

```md
Assisted-by: Claude Opus 5.5
```

```md
[Summary of change]

<details>

<summary>Assisted-by: Claude Opus 5.5</summary>

Details of change

</details>
```
produces:

[Summary of change]

<details>

<summary>Assisted-by: Claude Opus 5.5</summary>

Details of change

</details>

```md
> [!NOTE]  
> The following table was generated using Claude Opus 5.5
```
produces:
> [!NOTE]
> The following table was generated using Claude Opus 5.5