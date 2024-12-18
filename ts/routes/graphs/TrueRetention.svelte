<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphsResponse } from "@generated/anki/stats_pb";
    import * as tr from "@generated/ftl";

    import { type RevlogRange } from "./graph-helpers";
    import { DisplayMode, type PeriodTrueRetentionData, Scope } from "./true-retention";

    import Graph from "./Graph.svelte";
    import InputBox from "./InputBox.svelte";
    import TrueRetentionCombined from "./TrueRetentionCombined.svelte";
    import TrueRetentionSingle from "./TrueRetentionSingle.svelte";
    import { assertUnreachable } from "@tslib/typing";

    interface Props {
        revlogRange: RevlogRange;
        sourceData: GraphsResponse | null;
    }

    const { revlogRange, sourceData = null }: Props = $props();

    const retentionData: PeriodTrueRetentionData | null = $derived.by(() => {
        if (sourceData === null) {
            return null;
        } else {
            // Assert that all the True Retention data will be defined
            return sourceData.trueRetention as PeriodTrueRetentionData;
        }
    });

    let mode: DisplayMode = $state(DisplayMode.Summary);

    const title = tr.statisticsTrueRetentionTitle();
    const subtitle = tr.statisticsTrueRetentionSubtitle();
</script>

<Graph {title} {subtitle}>
    <InputBox>
        <label>
            <input type="radio" bind:group={mode} value={DisplayMode.Young} />
            {tr.statisticsCountsYoungCards()}
        </label>

        <label>
            <input type="radio" bind:group={mode} value={DisplayMode.Mature} />
            {tr.statisticsCountsMatureCards()}
        </label>

        <label>
            <input type="radio" bind:group={mode} value={DisplayMode.Summary} />
            {tr.statisticsTrueRetentionAll()}
        </label>
    </InputBox>

    <div class="table-container">
        {#if retentionData === null}
            <div>{tr.statisticsNoData()}</div>
        {:else if mode === DisplayMode.Young}
            <TrueRetentionSingle
                {revlogRange}
                data={retentionData}
                scope={Scope.Young}
            />
        {:else if mode === DisplayMode.Mature}
            <TrueRetentionSingle
                {revlogRange}
                data={retentionData}
                scope={Scope.Mature}
            />
        {:else if mode === DisplayMode.All}
            <TrueRetentionSingle {revlogRange} data={retentionData} scope={Scope.All} />
        {:else if mode === DisplayMode.Summary}
            <TrueRetentionCombined {revlogRange} data={retentionData} />
        {:else}
            {assertUnreachable(mode)}
        {/if}
    </div>
</Graph>

<style>
    .table-container {
        margin-top: 1rem;
        overflow-x: auto;

        display: flex;
        justify-content: center;
        align-items: center;
    }
</style>
