<script lang="typescript">
    import { I18n } from "../i18n";
    import { RevlogRange, GraphRange } from "./graphs";
    import { timeSpan, MONTH, YEAR } from "../time";

    export let i18n: I18n;
    export let revlogRange: RevlogRange;
    export let graphRange: GraphRange;

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
