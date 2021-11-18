<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Container from "../components/Container.svelte";
    import MapperRow from "./MapperRow.svelte";
    import type { ChangeNotetypeState, MapContext } from "./lib";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;

    let info = state.info;
</script>

<Container --gutter-inline="0.5rem" --gutter-block="0.15rem">
    {#each $info.mapForContext(ctx) as _, newIndex}
        <MapperRow {state} {ctx} {newIndex} />
    {/each}
</Container>
