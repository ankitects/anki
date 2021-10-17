<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../lib/ftl";
    import type { Stats } from "../lib/proto";
    import { getCardStats } from "./lib";
    import CardStats from "./CardStats.svelte";
    import Revlog from "./Revlog.svelte";

    export let cardId: number | undefined = undefined;
    export let includeRevlog: boolean | undefined = undefined;

    let stats: Stats.CardStatsResponse | undefined = undefined;

    $: if (cardId === undefined) {
        stats = undefined;
    } else {
        const sentCardId = cardId;
        getCardStats(sentCardId).then((s) => {
            /* Skip if another update has been triggered in the meantime. */
            if (sentCardId === cardId) {
                stats = s;
            }
        });
    };
</script>

<div class="container">
    <div>
        {#if stats}
            <CardStats {stats} />
            {#if includeRevlog}
                <Revlog {stats} />
            {/if}
        {:else}
            <span class="placeholder">{tr.cardStatsNoCard()}</span>
        {/if}
    </div>
</div>

<style>
    .container {
        max-width: 40em;
    }

    .placeholder {
        text-align: center;
    }
</style>
