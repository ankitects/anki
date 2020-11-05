<script lang="typescript">
    import type { I18n } from "anki/i18n";
    import { RevlogRange, GraphRange } from "./graph-helpers";
    import { timeSpan, MONTH, YEAR } from "anki/time";

    export let i18n: I18n;
    export let revlogRange: RevlogRange;
    export let graphRange: GraphRange;
    export let followRevlog: boolean = false;

    function onFollowRevlog(revlogRange: RevlogRange) {
        if (revlogRange === RevlogRange.All) {
            graphRange = GraphRange.AllTime;
        } else if (graphRange === GraphRange.AllTime) {
            graphRange = GraphRange.Year;
        }
    }

    $: if (followRevlog) {
        // split into separate function so svelte does not
        // run this when graphRange changes
        onFollowRevlog(revlogRange);
    }

    const month = timeSpan(i18n, 1 * MONTH);
    const month3 = timeSpan(i18n, 3 * MONTH);
    const year = timeSpan(i18n, 1 * YEAR);
    const all = i18n.tr(i18n.TR.STATISTICS_RANGE_ALL_TIME);
</script>

<label>
    <input type="radio" bind:group={graphRange} value={GraphRange.Month} />
    {month}
</label>
<label>
    <input type="radio" bind:group={graphRange} value={GraphRange.ThreeMonths} />
    {month3}
</label>
<label>
    <input type="radio" bind:group={graphRange} value={GraphRange.Year} />
    {year}
</label>
{#if revlogRange === RevlogRange.All}
    <label>
        <input type="radio" bind:group={graphRange} value={GraphRange.AllTime} />
        {all}
    </label>
{/if}
