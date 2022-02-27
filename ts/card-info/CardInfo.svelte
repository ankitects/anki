<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import type { Stats } from "../lib/proto";
    import { Cards, stats as statsService } from "../lib/proto";
    import CardInfoPlaceholder from "./CardInfoPlaceholder.svelte";
    import CardStats from "./CardStats.svelte";
    import Revlog from "./Revlog.svelte";

    export let cardId: number | null = null;
    export let includeRevlog: boolean = true;

    let stats: Stats.CardStatsResponse | null = null;
    let revlog: Stats.CardStatsResponse.StatsRevlogEntry[] | null = null;

    async function updateStats(cardId: number): Promise<void> {
        const requestedCardId = cardId;
        const cardStats = await statsService.cardStats(
            Cards.CardId.create({ cid: requestedCardId }),
        );

        /* Skip if another update has been triggered in the meantime. */
        if (requestedCardId === cardId) {
            stats = cardStats;

            if (includeRevlog) {
                revlog = stats.revlog as Stats.CardStatsResponse.StatsRevlogEntry[];
            }
        }
    }

    $: if (cardId) {
        updateStats(cardId);
    } else {
        stats = null;
        revlog = null;
    }
</script>

<Container breakpoint="md" --gutter-inline="1rem" --gutter-block="0.5rem">
    {#if stats}
        <Row>
            <CardStats {stats} />
        </Row>

        {#if revlog}
            <Row>
                <Revlog {revlog} />
            </Row>
        {/if}
    {:else}
        <CardInfoPlaceholder />
    {/if}
</Container>
