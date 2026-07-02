<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Container from "$lib/components/Container.svelte";
    import Spacer from "$lib/components/Spacer.svelte";

    import type { ChangeNotetypeState, MapContext } from "./lib";
    import MapperRow from "./MapperRow.svelte";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;

    const info = state.info;
</script>

<Spacer --height="0.5rem" />

<Container --gutter-inline="0.5rem" --gutter-block="0.15rem">
    {#each $info.mapForContext(ctx) as _, newIndex}
        <MapperRow {state} {ctx} {newIndex} />
    {/each}
</Container>
