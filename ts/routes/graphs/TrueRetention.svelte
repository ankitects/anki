<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphsResponse } from "@generated/anki/stats_pb";
    import * as tr from "@generated/ftl";

    import { renderTrueRetention } from "./true-retention";
    import Graph from "./Graph.svelte";
    import type { RevlogRange } from "./graph-helpers";

    export let revlogRange: RevlogRange;
    export let sourceData: GraphsResponse | null = null;

    let trueRetentionHtml: string;

    $: if (sourceData) {
        trueRetentionHtml = renderTrueRetention(sourceData, revlogRange);
    }

    const title = tr.statisticsTrueRetentionTitle();
    const subtitle = tr.statisticsTrueRetentionSubtitle();
</script>

<Graph {title} {subtitle}>
    {#if trueRetentionHtml}
        <div class="true-retention-table">
            {@html trueRetentionHtml}
        </div>
    {/if}
</Graph>

<style>
    .true-retention-table {
        overflow-x: auto;
        margin-top: 1rem;
    }
</style>
