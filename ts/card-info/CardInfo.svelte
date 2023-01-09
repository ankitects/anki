<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Stats } from "@tslib/proto";
    import { Cards, stats as statsService } from "@tslib/proto";

    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import CardInfoPlaceholder from "./CardInfoPlaceholder.svelte";
    import CardStats from "./CardStats.svelte";
    import Revlog from "./Revlog.svelte";

    export let includeRevlog: boolean = true;

    let stats: Stats.CardStatsResponse | null = null;
    let revlog: Stats.CardStatsResponse.StatsRevlogEntry[] | null = null;

    export async function updateStats(cardId: number | null): Promise<void> {
        const requestedCardId = cardId;

        if (cardId === null) {
            stats = null;
            revlog = null;
            return;
        }

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
