<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { DashboardResponse } from "@generated/anki/stats_pb";
    import { getDashboard } from "@generated/backend";
    import * as tr from "@generated/ftl";
    import { onMount } from "svelte";

    import type { Confidence, Estimate } from "./metrics";
    import { computeDashboard } from "./metrics";

    // When non-zero, scope to this deck; 0 = whole collection (the exam).
    export let deckId = 0n;

    let response: DashboardResponse | null = null;
    let loading = true;
    let error = "";

    async function load(): Promise<void> {
        loading = true;
        error = "";
        try {
            response = await getDashboard({ search: "", deckId });
        } catch (err) {
            error = String(err);
        } finally {
            loading = false;
        }
    }

    function refresh(): void {
        // auto-refresh only when idle and the window is actually on screen
        if (!loading && document.visibilityState === "visible") {
            void load();
        }
    }

    onMount(() => {
        void load();
        const timer = window.setInterval(refresh, 2500);
        document.addEventListener("visibilitychange", refresh);
        window.addEventListener("focus", refresh);
        return () => {
            window.clearInterval(timer);
            document.removeEventListener("visibilitychange", refresh);
            window.removeEventListener("focus", refresh);
        };
    });

    $: data = response
        ? computeDashboard(
              response.tags.map((t) => ({
                  tag: t.tag,
                  total: t.total,
                  studied: t.studied,
                  meanRetrievability: t.meanRetrievability,
                  reviewed: t.reviewed,
                  seen: t.seen,
              })),
              response.gradedReviews,
          )
        : null;

    function pct(x: number | null | undefined): string {
        return x == null ? "—" : `${Math.round(x * 100)}%`;
    }

    function rangeStr(r: Estimate | null): string {
        return r ? `${Math.round(r.low * 100)}–${Math.round(r.high * 100)}%` : "—";
    }

    function memoryColor(m: number | null): string {
        if (m == null) {
            return "var(--fg-faint)";
        }
        if (m >= 0.9) {
            return "var(--state-review)";
        }
        if (m >= 0.7) {
            return "var(--state-buried)";
        }
        return "var(--state-learn)";
    }

    function confidenceLabel(c: Confidence): string {
        if (c === "high") {
            return tr.statisticsDashboardConfidenceHigh();
        }
        if (c === "medium") {
            return tr.statisticsDashboardConfidenceMedium();
        }
        return tr.statisticsDashboardConfidenceLow();
    }
</script>

<div class="dashboard">
    <header class="head">
        <h1>{tr.statisticsCfaDashboard()}</h1>
        {#if data}
            <div class="meta">
                <span>{tr.statisticsDashboardCoverage()}: {pct(data.coverage)}</span>
                <span>
                    {tr.statisticsDashboardReviewsCount({ count: data.gradedReviews })}
                </span>
            </div>
        {/if}
    </header>

    {#if error && !data}
        <div class="message">{error}</div>
    {:else if !data}
        <div class="message">…</div>
    {:else}
        {#if !data.hasFsrsData}
            <div class="fsrs-hint">{tr.statisticsDashboardNeedsFsrs()}</div>
        {/if}
        <div class="gauges">
            <div class="gauge">
                <div class="gauge-title">{tr.statisticsDashboardMemory()}</div>
                {#if data.memory}
                    <div class="gauge-value">{pct(data.memory.point)}</div>
                    <div class="gauge-range">{rangeStr(data.memory)}</div>
                {:else}
                    <div class="gauge-empty">
                        {tr.statisticsDashboardInsufficient()}
                    </div>
                {/if}
                <div class="gauge-sub">{tr.statisticsDashboardMemorySubtitle()}</div>
            </div>

            <div class="gauge">
                <div class="gauge-title">
                    {tr.statisticsDashboardPerformance()}
                    <span class="badge">{tr.statisticsDashboardUncalibrated()}</span>
                </div>
                {#if data.performance}
                    <div class="gauge-value">{pct(data.performance.point)}</div>
                    <div class="gauge-range">{rangeStr(data.performance)}</div>
                {:else}
                    <div class="gauge-empty">
                        {tr.statisticsDashboardInsufficient()}
                    </div>
                {/if}
                <div class="gauge-sub">
                    {tr.statisticsDashboardPerformanceSubtitle()}
                </div>
            </div>

            <div class="gauge">
                <div class="gauge-title">{tr.statisticsDashboardReadiness()}</div>
                {#if data.readiness.abstained || !data.readiness.pPass}
                    <div class="gauge-empty">
                        {tr.statisticsDashboardInsufficient()}
                    </div>
                    <div class="gauge-note">
                        {tr.statisticsDashboardGiveUpNote()}
                    </div>
                {:else}
                    <div class="gauge-value">{pct(data.readiness.pPass.point)}</div>
                    <div class="gauge-range">{rangeStr(data.readiness.pPass)}</div>
                    <div class="gauge-sub">
                        {tr.statisticsDashboardConfidence()}: {confidenceLabel(
                            data.readiness.confidence,
                        )}
                    </div>
                {/if}
                <div class="gauge-sub">{tr.statisticsDashboardReadinessSubtitle()}</div>
            </div>
        </div>

        {#if data.bestNext}
            <div class="best-next">
                {tr.statisticsDashboardStudyNext()}:
                <strong>{data.bestNext}</strong>
            </div>
        {/if}

        <table class="subjects">
            <thead>
                <tr>
                    <th>{tr.statisticsDashboardSubject()}</th>
                    <th class="num">{tr.statisticsDashboardWeight()}</th>
                    <th>{tr.statisticsDashboardMemory()}</th>
                    <th class="num">{tr.statisticsDashboardPerformance()}</th>
                    <th class="num">{tr.statisticsDashboardStudied()}</th>
                </tr>
            </thead>
            <tbody>
                {#each data.subjects as s}
                    <tr class:dim={!s.covered}>
                        <td>{s.topic}</td>
                        <td class="num">{s.weight}%</td>
                        <td class="mem">
                            {#if s.memory != null}
                                <span class="bar">
                                    <span
                                        class="fill"
                                        style="width: {Math.round(
                                            s.memory * 100,
                                        )}%; background: {memoryColor(s.memory)};"
                                    ></span>
                                </span>
                                <span class="mem-pct">{pct(s.memory)}</span>
                            {:else}
                                <span class="not-studied">
                                    {tr.statisticsDashboardNotStudied()}
                                </span>
                            {/if}
                        </td>
                        <td class="num">{pct(s.performance)}</td>
                        <td class="num">{s.seen}/{s.total}</td>
                    </tr>
                {/each}
            </tbody>
        </table>
    {/if}
</div>

<style lang="scss">
    .dashboard {
        max-width: 60em;
        margin: 0 auto;
        padding: 1em;
        color: var(--fg);
        font-size: var(--font-size);
    }

    .head {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        justify-content: space-between;
        gap: 0.5em;

        h1 {
            font-size: 1.3em;
            font-weight: 600;
            margin: 0;
        }

        .meta {
            display: flex;
            gap: 1em;
            color: var(--fg-subtle);
            font-size: 0.9em;
        }
    }

    .gauges {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 1em;
        margin: 1em 0;

        @media (max-width: 620px) {
            grid-template-columns: 1fr;
        }
    }

    .gauge {
        background: var(--canvas-elevated);
        border: 1px solid var(--border-subtle);
        border-radius: var(--border-radius-medium);
        padding: 1em;
        text-align: center;
    }

    .gauge-title {
        font-weight: 600;
        display: flex;
        gap: 0.4em;
        align-items: center;
        justify-content: center;
    }

    .gauge-value {
        font-size: 2.2em;
        font-weight: 700;
        line-height: 1.1;
        margin-top: 0.2em;
    }

    .gauge-range {
        color: var(--fg-subtle);
    }

    .gauge-empty {
        font-size: 1.1em;
        font-weight: 600;
        color: var(--fg-subtle);
        margin: 0.6em 0;
    }

    .gauge-sub,
    .gauge-note {
        color: var(--fg-subtle);
        font-size: 0.85em;
        margin-top: 0.35em;
    }

    .badge {
        font-size: 0.7em;
        font-weight: 500;
        padding: 0.1em 0.45em;
        border-radius: var(--border-radius);
        background: var(--canvas-inset);
        border: 1px solid var(--border-subtle);
        color: var(--fg-subtle);
    }

    .best-next {
        margin: 0.5em 0 1em;
        padding: 0.5em 0.75em;
        background: var(--canvas-inset);
        border: 1px solid var(--border-subtle);
        border-radius: var(--border-radius);
    }

    .fsrs-hint {
        margin: 0.5em 0 1em;
        padding: 0.6em 0.8em;
        color: var(--fg-subtle);
        background: var(--canvas-inset);
        border: 1px solid var(--border);
        border-radius: var(--border-radius);
    }

    .subjects {
        width: 100%;
        border-collapse: collapse;

        th,
        td {
            padding: 0.4em 0.5em;
            border-bottom: 1px solid var(--border-subtle);
            text-align: left;
        }

        th {
            color: var(--fg-subtle);
            font-weight: 600;
        }

        .num {
            text-align: right;
            white-space: nowrap;
        }

        tr.dim {
            opacity: 0.55;
        }
    }

    .mem {
        min-width: 10em;

        .bar {
            display: inline-block;
            width: 6em;
            height: 0.7em;
            vertical-align: middle;
            background: var(--canvas-inset);
            border-radius: var(--border-radius);
            overflow: hidden;
        }

        .fill {
            display: block;
            height: 100%;
        }

        .mem-pct {
            margin-left: 0.5em;
            color: var(--fg-subtle);
        }

        .not-studied {
            color: var(--fg-faint);
        }
    }
</style>
