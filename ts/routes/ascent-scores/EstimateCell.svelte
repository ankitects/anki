<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { AscentEstimate } from "@generated/anki/stats_pb";

    export let estimate: AscentEstimate | undefined;
    export let level: number;

    // A number is only ever drawn when the backend says it is trustworthy.
    // Otherwise the cell shows why it is withheld and what would unlock it --
    // deliberately, not as an error state.
    $: reportable = estimate?.reportable ?? false;
    $: pct = (v: number) => `${Math.round(v * 100)}%`;
</script>

{#if reportable && estimate && estimate.point !== undefined}
    <div class="value">{pct(estimate.point)}</div>
    <div class="range" title="{level}% interval">
        <div class="track">
            <div
                class="span"
                style:left="{estimate.lower * 100}%"
                style:width="{(estimate.upper - estimate.lower) * 100}%"
            ></div>
            <div class="tick" style:left="{estimate.point * 100}%"></div>
        </div>
        <span class="bounds">
            {pct(estimate.lower)}–{pct(estimate.upper)}
            <span class="level">({level}% range)</span>
        </span>
    </div>
{:else}
    <div class="withheld">Not shown yet</div>
    <div class="why">{estimate?.reason ?? "no data"}</div>
    {#if estimate?.unlock}
        <div class="unlock">{estimate.unlock}</div>
    {/if}
{/if}

<style lang="scss">
    .value {
        font-size: 1.5em;
        font-variant-numeric: tabular-nums;
        line-height: 1.1;
    }

    .track {
        position: relative;
        height: 6px;
        margin: 0.4em 0 0.25em;
        border-radius: 3px;
        background: var(--canvas-inset);
        border: 1px solid var(--border-subtle);
    }

    .span {
        position: absolute;
        top: 0;
        bottom: 0;
        background: var(--fg-subtle);
        border-radius: 3px;
    }

    .tick {
        position: absolute;
        top: -3px;
        bottom: -3px;
        width: 2px;
        background: var(--fg);
    }

    .bounds {
        font-variant-numeric: tabular-nums;
        font-size: 0.85em;
        color: var(--fg-subtle);
    }

    .level {
        opacity: 0.75;
    }

    .withheld {
        font-size: 1.05em;
        font-style: italic;
        color: var(--fg-subtle);
    }

    .why,
    .unlock {
        font-size: 0.85em;
        margin-top: 0.2em;
    }

    .why {
        color: var(--fg-subtle);
    }

    .unlock {
        color: var(--fg);
    }
</style>
