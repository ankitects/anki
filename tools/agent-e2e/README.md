# Agent desktop smoke test

Requires Python 3.10+, Docker with BuildKit running, and Codex CLI 0.160.1
(`npm install -g @openai/codex@0.160.1`). Run `codex login` once locally.

```sh
python3 tools/agent-e2e/run.py
python3 tools/agent-e2e/run.py --watch  # watch the desktop in a browser
```

The runner builds this checkout, starts an isolated Linux desktop, and gives Codex
`prompt.md`. Its Dockerfile is self-contained. First builds take several minutes;
unchanged builds use Docker's cache. This tests Linux Anki, not installers.

Computer use follows [OpenAI's PyAutoGUI code-execution example](https://developers.openai.com/api/docs/guides/tools-computer-use#use-code-execution).
`computer.py` is the small MCP adapter; Codex handles the agent loop. Python runs
inside the container; credentials stay on the host. UI-only testing is a prompt
instruction, not a restriction on Python inside the container.

The default model is `gpt-6-sol` with low reasoning; override with `ANKI_E2E_MODEL`.
There is a five-minute agent timeout. No retries. Results go in `out/agent-e2e/`:
`result.txt`, `replay-5x.mp4`, the last screenshot, and `desktop.log`.
Exit 0 means the agent reported `PASS`; failures exit nonzero. This MVP trusts the
agent's verdict. Review the video when investigating a failure or questionable pass.

For CI, add the `OPENAI_API_KEY` repository secret. Pushes to
`e2e-computer-use-testing` run it before merge. Once registered, it can also be
dispatched through GitHub CLI; the Actions UI button needs it on the default branch.
It runs the same script and uploads the `agent-e2e` artifact, including on failure.
Download that artifact for the result and video. Nothing is posted to PRs.
