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

    export let cardId: number | null = null;
    export let includeRevlog: boolean = true;

    let stats: Stats.CardStatsResponse | undefined;

    $: if (cardId === null) {
        stats = undefined;
    } else {
        const requestedCardId = cardId;
        getCardStats(requestedCardId).then((s) => {
            /* Skip if another update has been triggered in the meantime. */
            if (requestedCardId === cardId) {
                stats = s;
            }
        });
    }
</script>

{#if stats}
    <div class="container">
        <div>
            <CardStats {stats} />
            {#if includeRevlog}
                <Revlog {stats} />
            {/if}
        </div>
    </div>
{:else}
    <div class="placeholder">{tr.cardStatsNoCard()}</div>
{/if}

<style>
    .container {
        max-width: 40em;
    }

    .placeholder {
        margin: 0;
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
    }
</style>
