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
    export let fsrsEnabled: boolean = stats?.memoryState != null;
    export let desiredRetention: number = stats?.desiredRetention ?? 0.9;
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
        {#if fsrsEnabled}
            <Row>
                <ForgettingCurve revlog={stats.revlog} {desiredRetention} />
            </Row>
        {/if}
    {:else}
        <CardInfoPlaceholder />
    {/if}
</Container>
