<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphsResponse } from "@generated/anki/stats_pb";
    import { renderTrueRetention } from "./true-retention";
    import Graph from "./Graph.svelte";

    export let sourceData: GraphsResponse | null = null;

    let trueRetentionHtml: string;

    $: if (sourceData) {
        trueRetentionHtml = renderTrueRetention(sourceData);
    }

    const title = "True Retention";
</script>

<Graph {title}>
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
