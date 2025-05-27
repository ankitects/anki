<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { CardStatsResponse } from "@generated/anki/stats_pb";

    import Container from "$lib/components/Container.svelte";
    import Row from "$lib/components/Row.svelte";

    import CardInfoPlaceholder from "./CardInfoPlaceholder.svelte";
    import CardStats from "./CardStats.svelte";
    import Revlog from "./Revlog.svelte";
    import ForgettingCurve from "./ForgettingCurve.svelte";

    export let stats: CardStatsResponse | null = null;
    export let showRevlog: boolean = true;
    export let showCurve: boolean = true;

    $: fsrsEnabled = stats?.memoryState != null;
    $: desiredRetention = stats?.desiredRetention ?? 0.9;
    $: decay = (() => {
        const paramsLength = stats?.fsrsParams?.length ?? 0;
        if (paramsLength === 0) {
            return 0.2; // default decay for FSRS-6
        }
        if (paramsLength < 21) {
            return 0.5; // default decay for FSRS-4.5 and FSRS-5
        }
        return stats?.fsrsParams?.[20] ?? 0.2;
    })();
</script>

<Container breakpoint="md" --gutter-inline="1rem" --gutter-block="0.5rem">
    {#if stats}
        <Row>
            <CardStats {stats} />
        </Row>

        {#if showRevlog}
            <Row>
                <Revlog revlog={stats.revlog} {fsrsEnabled} />
            </Row>
        {/if}
        {#if fsrsEnabled && showCurve}
            <Row>
                <ForgettingCurve revlog={stats.revlog} {desiredRetention} {decay} />
            </Row>
        {/if}
    {:else}
        <CardInfoPlaceholder />
    {/if}
</Container>
