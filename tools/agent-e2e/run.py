#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Build Anki, run a Codex UI smoke test, and save a 5× recording."""

import argparse
import json
import os
import shlex
import subprocess
import tempfile
import webbrowser
from pathlib import Path


def run(command, *args, **kwargs):
    return subprocess.run([*shlex.split(command), *args], check=True, **kwargs)


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--watch", action="store_true")
args = parser.parse_args()
harness = Path(__file__).resolve().parent
repo = harness.parent.parent
output = repo / "out/agent-e2e"
output.mkdir(parents=True, exist_ok=True)
artifacts = Path(tempfile.mkdtemp(prefix="run-", dir=output))
print(f"Results and 5× video: {artifacts}", flush=True)

run("git submodule update --init ftl/core-repo ftl/qt-repo", cwd=repo)
run("docker build -f tools/agent-e2e/Dockerfile -t anki-agent-e2e .", cwd=repo)
name = f"anki-e2e-{os.getpid()}"
ports = ["-p", "127.0.0.1::6080"] if args.watch else []
run(
    "docker run -d --shm-size=2g --name",
    name,
    "-v",
    f"{artifacts}:/artifacts",
    "-v",
    f"{harness}:/harness:ro",
    *ports,
    "anki-agent-e2e",
)
try:
    run(
        "docker exec",
        name,
        "timeout",
        "60",
        "sh",
        "-c",
        "until test -f /artifacts/.ready; do sleep 0.1; done",
    )
    if args.watch:
        address = run(
            "docker port", name, "6080", capture_output=True, text=True
        ).stdout.strip()
        url = f"http://{address}/vnc.html?autoconnect=1&resize=scale&view_only=true"
        print(f"Watch: {url}", flush=True)
        webbrowser.open(url)

    # Give Codex only the container's computer tool, independent of local settings.
    config = {
        "model_reasoning_effort": "low",
        "approval_policy": "never",
        "project_doc_max_bytes": 0,
        "web_search": "disabled",
        "features.shell_tool": False,
        "features.skip_host_skill_discovery": True,
        "mcp_servers.desktop.command": "docker",
        "mcp_servers.desktop.args": [
            "exec",
            "-i",
            name,
            "python",
            "/harness/computer.py",
        ],
        "mcp_servers.desktop.required": True,
        "mcp_servers.desktop.default_tools_approval_mode": "approve",
    }
    options = []
    for key, value in config.items():
        options += ["-c", f"{key}={json.dumps(value)}"]
    run(
        "codex exec --ignore-user-config --skip-git-repo-check --ephemeral -s read-only",
        "-C",
        str(artifacts),
        "--model",
        os.environ.get("ANKI_E2E_MODEL", "gpt-6-sol"),
        "-o",
        str(artifacts / "result.txt"),
        *options,
        "-",
        input=(harness / "prompt.md").read_text(),
        text=True,
        timeout=300,
    )
    result = (artifacts / "result.txt").read_text()
    if result.split()[0] != "PASS":
        raise SystemExit(1)
finally:
    run("docker stop -t 10", name)
    run("docker rm", name)
    print(f"Results and 5× video: {artifacts}", flush=True)
