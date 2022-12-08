<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Spacer from "../components/Spacer.svelte";
    import type { ChangeNotetypeState, MapContext } from "./lib";
    import MapperRow from "./MapperRow.svelte";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;

    const info = state.info;
</script>

<Spacer --height="0.5rem" />

{#each $info.mapForContext(ctx) as _, newIndex}
    <MapperRow {state} {ctx} {newIndex} />
{/each}
