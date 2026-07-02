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
* AI tools **should not** be used to produce GitHub comments, including filling in the pull request 
  template.
* Contributions that make nontrivial use of AI tools **must** be labelled, with the tool version.

### Handling Questions about AI Use

If you are not sure whether a tool or workflow counts as AI usage under this policy, please ask
**before** using it. Ask your question in a public [#ankidroid-dev](https://discord.gg/xeb7bBZVJ6) so that the answer
helps everyone. Direct messages to mentors/reviewers about AI usage may not get a reply.

If a reviewer raises concerns about AI usage in your contribution, reply clearly and honestly with
facts. Please do not argue about the policy in PRs. Repeated arguments, hiding AI usage,
or trying to bypass this policy may lead to moderator action.

### Disclosure

**Commit messages** **must** use the `Assisted-by:` 
 [git trailer](https://git-scm.com/docs/git-interpret-trailers#_description) with an explanation
 of the contributions in the description.


```
docs: example title

[Optional commit description]

GPT-5.2 implemented `complexMethod`

Assisted-by: GPT-5.2
Assisted-by: Claude Opus 4.5 [Proofreading/grammar]
```

**GitHub comments** **must** use the `> [!NOTE]` 
 [alert](https://docs.github.com/en/get-started/writing-on-github/getting-started-with-writing-and-formatting-on-github/basic-writing-and-formatting-syntax#alerts).

```md
> [!NOTE]  
> The following table was generated using Claude Opus 4.5 
```
produces:
> [!NOTE]  
> The following table was generated using Claude Opus 4.5