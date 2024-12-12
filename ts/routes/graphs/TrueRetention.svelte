<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type {GraphsResponse} from "@generated/anki/stats_pb";
    import * as t9n from "@generated/ftl";

    import {type RevlogRange} from "./graph-helpers";
    import {
        type PeriodTrueRetentionData,
        Scope,
    } from "./true-retention";

    import Graph from "./Graph.svelte";
    import InputBox from "./InputBox.svelte";
    import TrueRetentionCombined from "./TrueRetentionCombined.svelte";
    import TrueRetentionSingle from "./TrueRetentionSingle.svelte";

    interface Props {
        revlogRange: RevlogRange,
        sourceData: GraphsResponse | null,
    }

    let {
        revlogRange,
        sourceData = null,
    }: Props = $props();

    let retentionData: PeriodTrueRetentionData | null = $derived.by(() => {
        if (sourceData === null) {
            return null;
        } else {
            // Assert that all the True Retention data will be defined
            return sourceData.trueRetention as PeriodTrueRetentionData
        }
    });

    enum Mode {
        Young,
        Mature,
        All,
        Summary,
    }

    let mode: Mode = $state(Mode.Summary);

    const title = t9n.statisticsTrueRetentionTitle();
    const subtitle = t9n.statisticsTrueRetentionSubtitle();
</script>

<Graph {title} {subtitle}>
    <InputBox>
        <label>
            <input type="radio" bind:group={mode} value={Mode.Young} />
            {t9n.statisticsCountsYoungCards()}
        </label>

        <label>
            <input type="radio" bind:group={mode} value={Mode.Mature} />
            {t9n.statisticsCountsMatureCards()}
        </label>

        <label>
            <input type="radio" bind:group={mode} value={Mode.All} />
            {t9n.statisticsTrueRetentionAll()}
        </label>

        <label>
            <input type="radio" bind:group={mode} value={Mode.Summary} />
            {t9n.statisticsTrueRetentionSummary()}
        </label>
    </InputBox>

    <div class="table-container">
        {#if retentionData === null}
            <div>No Data!</div>
        {:else if mode === Mode.Young}
            <TrueRetentionSingle revlogRange={revlogRange} data={retentionData} scope={Scope.Young} />
        {:else if mode === Mode.Mature}
            <TrueRetentionSingle revlogRange={revlogRange} data={retentionData} scope={Scope.Mature} />
        {:else if mode === Mode.All}
            <TrueRetentionSingle revlogRange={revlogRange} data={retentionData} scope={Scope.All} />
        {:else if mode === Mode.Summary}
            <TrueRetentionCombined revlogRange={revlogRange} data={retentionData} />
        {:else}
            <div>Bad mode: {mode}</div>
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
