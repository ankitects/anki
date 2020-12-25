<script context="module">
    // import _ from "./graph-helpers.scss";
</script>

<script lang="typescript">
    import RangeBox from './RangeBox.svelte'

    import type { I18n } from "anki/i18n";
    import type pb from "anki/backend_proto";
    import { getGraphData, RevlogRange } from "./graph-helpers";

    export let i18n: I18n;
    export let nightMode: boolean;
    export let graphs: any[];

    export let search: string;
    export let days: number;
    export let withRangeBox: boolean;

    let sourceData: pb.BackendProto.GraphsOut | null = null;
    let revlogRange: RevlogRange;

    const refreshWith = async (search: string, days: number) => {
        try {
            sourceData = await getGraphData(search, days);
            revlogRange = days > 365 || days === 0
                ? RevlogRange.All
                : RevlogRange.Year;
        } catch (e) {
            sourceData = null;
            alert(i18n.tr(i18n.TR.STATISTICS_ERROR_FETCHING));
        }
    }

    let active = false;

    const refresh = (event: CustomEvent) => {
        active = true;
        refreshWith(event.detail.search, event.detail.days)
        active = false;
    }

    refreshWith(search, days)
</script>

{#if withRangeBox}
    <RangeBox {i18n} {search} {days} {active} on:update={refresh} />
{/if}

{#if sourceData}
    <div tabindex="-1" class="no-focus-outline">
        {#each graphs as Graph}
            <Graph {sourceData} {revlogRange} {i18n} {nightMode} />
        {/each}
    </div>
{/if}
