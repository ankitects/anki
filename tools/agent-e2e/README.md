# Agent desktop end to end test

Requires Python 3.10+, Docker with BuildKit running, and Codex CLI 0.160.1
(`npm install -g @openai/codex@0.160.1`). Run `codex login` once locally.

```sh
python3 tools/agent-e2e/run.py
python3 tools/agent-e2e/run.py --watch  # watch the desktop in a browser
```

The runner builds this checkout, starts an isolated Linux desktop, and gives Codex
`prompt.md`. This tests Linux Anki, not installers.

Computer use follows [OpenAI's PyAutoGUI code-execution example](https://developers.openai.com/api/docs/guides/tools-computer-use#use-code-execution) via `computer.py` MCP adapter.

The default model is `gpt-6-sol` with low reasoning and can be set with `ANKI_E2E_MODEL`.
There is a five-minute agent timeout and no retries so we can catch errors and bugs in the early days.  Results go in `out/agent-e2e/` with `result.txt`, `replay-5x.mp4`, and `desktop.log`.

An Exit 0 means the agent reported `PASS` with no failures.  Currently this trusts the agent's verdict, but we could later add more checks on anki state once agent finishes.

The video si so we can review and watch where it fails if there's an error, but also just watch it for a final pre-release confirmation.

For CI, add the `OPENAI_API_KEY` repository secret. Nothing is posted to PRs at the moment so will need to download the artifact to view the video result.
