<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { MONTH, timeSpan, YEAR } from "@tslib/time";

    import { GraphRange, RevlogRange } from "./graph-helpers";

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

    const month = timeSpan(1 * MONTH);
    const month3 = timeSpan(3 * MONTH);
    const year = timeSpan(1 * YEAR);
    const all = tr.statisticsRangeAllTime();
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
