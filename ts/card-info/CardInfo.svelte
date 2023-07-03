<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type {
        CardStatsResponse,
        CardStatsResponse_StatsRevlogEntry,
    } from "@tslib/anki/stats_pb";
    import { cardStats } from "@tslib/backend";

    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import CardInfoPlaceholder from "./CardInfoPlaceholder.svelte";
    import CardStats from "./CardStats.svelte";
    import Revlog from "./Revlog.svelte";

    export let includeRevlog: boolean = true;

    let stats: CardStatsResponse | null = null;
    let revlog: CardStatsResponse_StatsRevlogEntry[] | null = null;

    export async function updateStats(cardId: bigint | null): Promise<void> {
        const requestedCardId = cardId;

        if (cardId === null) {
            stats = null;
            revlog = null;
            return;
        }

        const updatedStats = await cardStats({ cid: cardId });

        /* Skip if another update has been triggered in the meantime. */
        if (requestedCardId === cardId) {
            stats = updatedStats;

            if (includeRevlog) {
                revlog = stats.revlog;
            }
        }
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
