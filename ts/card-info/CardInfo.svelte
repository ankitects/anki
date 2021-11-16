<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Stats } from "../lib/proto";
    import { getCardStats } from "./lib";
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import CardStats from "./CardStats.svelte";
    import CardInfoPlaceholder from "./CardInfoPlaceholder.svelte";
    import Revlog from "./Revlog.svelte";

    export let cardId: number | null = null;
    export let includeRevlog: boolean = true;

    let stats: Stats.CardStatsResponse | null = null;

    $: if (cardId === null) {
        stats = null;
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

<Container breakpoint="md" --gutter-inline="0.25rem" --gutter-block="0.5rem">
    {#if stats}
        <Row>
            <CardStats {stats} />
        </Row>

        {#if includeRevlog && stats.revlog}
            <Row>
                <Revlog revlog={stats.revlog} />
            </Row>
        {/if}
    {:else}
        <CardInfoPlaceholder />
    {/if}
</Container>
