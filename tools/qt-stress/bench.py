#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""A/B UI latency benchmark between Anki builds (e.g. Qt 6.11.2 vs 6.11.0).

    # run both builds, alternating, 3 launches each
    python3 tools/qt-stress/bench.py run \\
        --build "6.11.2=just run" \\
        --build "6.11.0=PYENV=out/pyenv611 just run"

    # compare result files, e.g. ones sent in by users running the add-on
    python3 tools/qt-stress/bench.py compare a.json b.json

Each launch gets a fresh copy of the same generated profile and collection,
and the qt_stress add-on drives the UI and writes its timings to JSON. Keep the
machine otherwise idle and don't cover the Anki window: occluded windows get
their frames throttled by macOS, which would swamp any real difference.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import shlex
import shutil
import subprocess
import time
from pathlib import Path
from statistics import median
from typing import Any

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
ADDON = HERE / "qt_stress"

# Collection generation (runs under the build's venv python, which has pylib)
##########################################################################

MAKE_COLLECTION = r"""
import os, random, sys
from pathlib import Path
# same as tools/run.py, for dev venvs; harmless for other pythons
sys.path.extend(["pylib", "qt", "out/pylib", "out/qt"])
from anki.collection import Collection
from aqt.profiles import ProfileManager

base = Path(sys.argv[1])
notes = int(sys.argv[2])
random.seed(5663)

pm = ProfileManager(ProfileManager.get_created_base_folder(str(base)))
pm.setupMeta()
pm.create("stress")
pm.load("stress")
pm.meta["firstRun"] = False
pm.meta["defaultLang"] = "en_US"
pm.save()

col_path = Path(pm.profileFolder()) / "collection.anki2"
col = Collection(str(col_path))
media = Path(col.media.dir())
for i in range(20):
    hue = i * 18
    (media / f"stress{i}.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="320" height="180">'
        f'<rect width="320" height="180" fill="hsl({hue},60%,55%)"/>'
        f'<circle cx="160" cy="90" r="{40 + i * 2}" fill="hsl({hue + 180},60%,45%)"/>'
        f'<text x="20" y="40" font-size="28">image {i}</text></svg>'
    )

words = ("mitochondria glycolysis enzyme substrate ribosome nucleus membrane "
         "osmosis diffusion catalyst receptor ligand pathway kinase").split()

def para(n):
    return " ".join(random.choice(words) for _ in range(n)).capitalize() + "."

model = col.models.by_name("Basic")
deck_id = col.decks.id("Default")
for i in range(notes):
    note = col.new_note(model)
    front = f"<b>Card {i}</b>: {para(12)}"
    if i % 3 == 0:
        front += f'<br><img src="stress{i % 20}.svg">'
    back = (
        f"<p>{para(40)}</p><ul>"
        + "".join(f"<li><i>{para(6)}</i></li>" for _ in range(5))
        + "</ul><table border=1>"
        + "".join(
            "<tr>" + "".join(f"<td>{random.choice(words)}</td>" for _ in range(4)) + "</tr>"
            for _ in range(4)
        )
        + "</table>"
    )
    if i % 5 == 0:
        back += f'<img src="stress{(i + 7) % 20}.svg">'
    note["Front"] = front
    note["Back"] = back
    col.add_note(note, deck_id)

# plenty of cards due today
conf = col.decks.config_dict_for_deck_id(deck_id)
conf["new"]["perDay"] = 9999
col.decks.update_config(conf)
col.close()
"""


def make_template(python: str, dest: Path, notes: int) -> None:
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True)
    subprocess.run(
        [python, "-c", MAKE_COLLECTION, str(dest), str(notes)],
        check=True,
        cwd=REPO,
    )
    shutil.copytree(ADDON, dest / "addons21" / "qt_stress")


# Running builds
##########################################################################


def parse_build(spec: str) -> tuple[str, str]:
    name, sep, cmd = spec.partition("=")
    if not sep or not cmd:
        raise SystemExit(f"--build must be NAME=COMMAND, got {spec!r}")
    return name.strip(), cmd.strip()


def run_once(
    name: str, cmd: str, template: Path, workdir: Path, args: argparse.Namespace
) -> dict[str, Any] | None:
    base = workdir / "base"
    if base.exists():
        shutil.rmtree(base)
    shutil.copytree(template, base)
    out = workdir / "result.json"
    out.unlink(missing_ok=True)

    env = os.environ.copy()
    env.update(
        QT_STRESS_OUT=str(out),
        QT_STRESS_ITERS=str(args.iters),
        QT_STRESS_WARMUP=str(args.warmup),
        # don't collide with an Anki instance the user has open
        ANKI_SINGLE_INSTANCE_KEY=f"qt-stress-{os.getpid()}",
        # keep dev builds closer to release builds
        ANKIDEV="0",
        QTWEBENGINE_REMOTE_DEBUGGING="",
        QTWEBENGINE_CHROMIUM_FLAGS="",
        ANKI_API_PORT="",
    )
    full = f"{cmd} -b {shlex.quote(str(base))}"
    log_path = workdir / "anki.log"
    print(f"  launching {name}: {full}", flush=True)
    with open(log_path, "w") as log:
        proc = subprocess.Popen(
            ["bash", "-c", full], cwd=REPO, env=env, stdout=log, stderr=log
        )
        try:
            proc.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            proc.kill()
            print(f"  {name} timed out, see {log_path}")
            return None
    if not out.exists():
        print(f"  {name} produced no results (exit {proc.returncode}), see {log_path}")
        return None
    return json.loads(out.read_text())


def cmd_run(args: argparse.Namespace) -> None:
    builds = [parse_build(b) for b in args.build]
    if len(builds) < 2:
        raise SystemExit("need at least two --build options")
    outdir = Path(args.out).resolve()
    outdir.mkdir(parents=True, exist_ok=True)

    template = outdir / "template"
    print(f"generating collection with {args.notes} notes...", flush=True)
    make_template(args.setup_python, template, args.notes)

    results: dict[str, list[dict[str, Any]]] = {name: [] for name, _ in builds}
    for rnd in range(args.rounds):
        # alternate order each round so drift (thermals, caches) is shared
        order = builds if rnd % 2 == 0 else list(reversed(builds))
        print(f"round {rnd + 1}/{args.rounds}", flush=True)
        for name, cmd in order:
            workdir = outdir / "work" / name
            workdir.mkdir(parents=True, exist_ok=True)
            data = run_once(name, cmd, template, workdir, args)
            if data:
                path = outdir / f"{name}-round{rnd + 1}.json"
                path.write_text(json.dumps(data, indent=1))
                results[name].append(data)
                m = data["meta"]
                print(
                    f"  {name}: Qt {m['qt']} / QtWebEngine {m['qtwebengine']}"
                    f" -> {path.name}",
                    flush=True,
                )
            if args.cooldown:
                time.sleep(args.cooldown)

    report = compare(results)
    print()
    print(report)
    (outdir / "report.md").write_text(report)
    print(f"\nreport written to {outdir / 'report.md'}")


def cmd_compare(args: argparse.Namespace) -> None:
    results: dict[str, list[dict[str, Any]]] = {}
    for path in args.files:
        data = json.loads(Path(path).read_text())
        m = data["meta"]
        key = f"Qt {m['qt']}" if args.group_by_qt else Path(path).stem
        results.setdefault(key, []).append(data)
    print(compare(results))


# Statistics
##########################################################################


def pct(values: list[float], p: float) -> float:
    s = sorted(values)
    k = (len(s) - 1) * p
    lo = int(k)
    hi = min(lo + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (k - lo)


def mann_whitney_p(a: list[float], b: list[float]) -> float:
    """Two-sided Mann-Whitney U p-value, normal approximation with tie
    correction. Samples within one launch aren't fully independent, which is
    why the report also checks whether per-launch medians overlap."""
    n1, n2 = len(a), len(b)
    if n1 < 3 or n2 < 3:
        return float("nan")
    combined = sorted([(v, 0) for v in a] + [(v, 1) for v in b])
    ranks = [0.0] * len(combined)
    tie_term = 0.0
    i = 0
    while i < len(combined):
        j = i
        while j + 1 < len(combined) and combined[j + 1][0] == combined[i][0]:
            j += 1
        r = (i + j) / 2 + 1
        for k in range(i, j + 1):
            ranks[k] = r
        t = j - i + 1
        tie_term += t**3 - t
        i = j + 1
    r1 = sum(r for r, (_, g) in zip(ranks, combined) if g == 0)
    u1 = r1 - n1 * (n1 + 1) / 2
    n = n1 + n2
    mu = n1 * n2 / 2
    sigma = math.sqrt(n1 * n2 / 12 * ((n + 1) - tie_term / (n * (n - 1))))
    if sigma == 0:
        return 1.0
    z = (abs(u1 - mu) - 0.5) / sigma
    return math.erfc(z / math.sqrt(2))


FRAME_MS = 1000 / 60

# Ordered roughly by how directly they map to user reports.
LATENCY_ORDER = [
    "startup_to_deck_list",
    "reviewer_show_question",
    "reviewer_show_answer",
    "reviewer_enter",
    "browser_switch_row",
    "browser_switch_row_with_preview",
    "browser_open",
    "previewer_open",
    "add_dialog_open",
    "deck_browser_refresh",
    "overview_refresh",
    "resize",
    "js_roundtrip",
]


def latency_metrics(runs: list[dict[str, Any]], key: str) -> tuple[list, list]:
    pooled: list[float] = []
    per_run: list[float] = []
    for r in runs:
        vals = r["samples"].get(key) or []
        if vals:
            pooled.extend(vals)
            per_run.append(median(vals))
    return pooled, per_run


def frame_metrics(values: list[float]) -> dict[str, float]:
    med = median(values)
    return dict(
        fps=len(values) / (sum(values) / 1000),
        p99=pct(values, 0.99),
        janky=sum(1 for v in values if v > med * 1.5) / len(values) * 100,
    )


def verdict(
    a_pooled: list[float],
    b_pooled: list[float],
    a_runs: list[float],
    b_runs: list[float],
) -> str:
    ma, mb = median(a_pooled), median(b_pooled)
    delta = mb - ma
    rel = delta / ma if ma else 0
    p = mann_whitney_p(a_pooled, b_pooled)
    separated = (
        len(a_runs) > 1
        and len(b_runs) > 1
        and (max(a_runs) < min(b_runs) or max(b_runs) < min(a_runs))
    )
    if abs(delta) < FRAME_MS or abs(rel) < 0.10 or not (p < 0.01):
        return "no meaningful difference"
    which = "slower" if delta > 0 else "faster"
    strength = "likely perceivable" if abs(delta) >= 50 else "measurable, sub-50ms"
    if not separated and len(a_runs) > 1:
        strength += ", inconsistent across launches"
    return f"B {which}: {strength}"


def describe(runs: list[dict[str, Any]]) -> str:
    m = runs[0]["meta"]
    return (
        f"Anki {m['anki']}, Qt {m['qt']}, QtWebEngine {m['qtwebengine']}, "
        f"Chromium {m['chromium']}, {m['platform']}, macOS {m['mac_ver'] or '-'}, "
        f"driver {m['video_driver']}, {m['screen_refresh_hz']}Hz "
        f"@{m['device_pixel_ratio']}x, {m['card_count']} cards, {len(runs)} launch(es)"
    )


def compare(results: dict[str, list[dict[str, Any]]]) -> str:
    names = [n for n, runs in results.items() if runs]
    if len(names) < 2:
        return "not enough successful runs to compare"
    a, b = names[0], names[1]
    A, B = results[a], results[b]
    out = [
        f"# Qt stress benchmark: A = {a}, B = {b}",
        "",
        f"- A: {describe(A)}",
        f"- B: {describe(B)}",
        "",
        "Times are milliseconds from triggering an action until its webviews have "
        "rendered and painted two more frames. Per-launch medians show "
        "launch-to-launch consistency.",
        "",
        "| operation | A median | B median | Δ median | A p90 | B p90 | "
        "A per-launch medians | B per-launch medians | MW p | verdict |",
        "|---|---:|---:|---:|---:|---:|---|---|---:|---|",
    ]
    keys = [k for k in LATENCY_ORDER] + sorted(
        {k for r in A + B for k in r["samples"]}
        - set(LATENCY_ORDER)
        - {"frames_idle", "frames_scroll"}
    )
    for key in keys:
        ap, ar = latency_metrics(A, key)
        bp, br = latency_metrics(B, key)
        if not ap or not bp:
            continue
        ma, mb = median(ap), median(bp)
        rel = (mb - ma) / ma * 100 if ma else 0
        if min(len(ap), len(bp)) < 3:
            # one sample per launch (startup, *_enter, *_open with few iters)
            v = "too few samples, use more --rounds"
        elif len(ap) == len(ar):
            # the launches are the samples, so there is nothing to cross-check
            v = verdict(ap, bp, [], [])
        else:
            v = verdict(ap, bp, ar, br)
        out.append(
            f"| {key} | {ma:.1f} | {mb:.1f} | {mb - ma:+.1f} ({rel:+.0f}%) | "
            f"{pct(ap, 0.9):.1f} | {pct(bp, 0.9):.1f} | "
            f"{', '.join(f'{x:.0f}' for x in ar)} | "
            f"{', '.join(f'{x:.0f}' for x in br)} | "
            f"{mann_whitney_p(ap, bp):.3g} | {v} |"
        )

    out += [
        "",
        "## Frame pacing (requestAnimationFrame while animating)",
        "",
        "| test | A fps | B fps | A p99 frame | B p99 frame | A janky % | B janky % |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for key in ("frames_idle", "frames_scroll"):
        av = [v for r in A for v in r["samples"].get(key, [])]
        bv = [v for r in B for v in r["samples"].get(key, [])]
        if not av or not bv:
            continue
        fa, fb = frame_metrics(av), frame_metrics(bv)
        out.append(
            f"| {key} | {fa['fps']:.1f} | {fb['fps']:.1f} | {fa['p99']:.1f} | "
            f"{fb['p99']:.1f} | {fa['janky']:.1f} | {fb['janky']:.1f} |"
        )

    out += [
        "",
        "## Main-thread stalls (event loop blocked > 50ms), summed over launches",
        "",
        "| phase | A count | B count | A worst | B worst | A total | B total |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    phases = sorted({p for r in A + B for p in r["stalls"]})
    for phase in phases:
        sa = [v for r in A for v in r["stalls"].get(phase, [])]
        sb = [v for r in B for v in r["stalls"].get(phase, [])]
        out.append(
            f"| {phase} | {len(sa)} | {len(sb)} | {max(sa, default=0):.0f} | "
            f"{max(sb, default=0):.0f} | {sum(sa):.0f} | {sum(sb):.0f} |"
        )

    errors = [
        f"- {name}: {phase}: {err}"
        for name, runs in ((a, A), (b, B))
        for r in runs
        for phase, err in r["errors"].items()
    ]
    if errors:
        out += ["", "## Errors", ""] + sorted(set(errors))
    return "\n".join(out)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = parser.add_subparsers(dest="command", required=True)

    run = sub.add_parser("run", help="launch builds and compare them")
    run.add_argument(
        "--build",
        action="append",
        required=True,
        help="NAME=COMMAND; '-b <base>' is appended to COMMAND. The first is A.",
    )
    run.add_argument("--rounds", type=int, default=3)
    run.add_argument("--iters", type=int, default=40)
    run.add_argument("--warmup", type=int, default=5)
    run.add_argument("--notes", type=int, default=2000)
    run.add_argument("--timeout", type=int, default=900, help="per launch, seconds")
    run.add_argument("--cooldown", type=int, default=5, help="seconds between launches")
    run.add_argument(
        "--setup-python",
        default=str(REPO / "out" / "pyenv" / "bin" / "python"),
        help="python with anki+aqt installed, used to generate the collection",
    )
    run.add_argument(
        "--out",
        default=str(REPO / "out" / "qt-stress"),
        help="directory for results",
    )
    run.set_defaults(func=cmd_run)

    cmp = sub.add_parser("compare", help="compare existing result JSON files")
    cmp.add_argument("files", nargs="+")
    cmp.add_argument(
        "--group-by-qt",
        action="store_true",
        help="group files by Qt version instead of treating each file separately",
    )
    cmp.set_defaults(func=cmd_compare)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
