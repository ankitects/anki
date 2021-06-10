<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import MapperRow from "./MapperRow.svelte";
    import { ChangeNotetypeState, MapContext } from "./lib";
    import { slide } from "svelte/transition";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;

    let info = state.info;

    let unused: string[];
    let unusedMsg: string;
    $: {
        unused = $info.unusedItems(ctx);
        unusedMsg =
            ctx === MapContext.Field
                ? tr.changeNotetypeWillDiscardContent()
                : tr.changeNotetypeWillDiscardCards();
    }
</script>

<div class="container m-1">
    <div class="row">
        <div class="col"><b>{tr.changeNotetypeCurrent()}</b></div>
        <div class="col"><b>{tr.changeNotetypeNew()}</b></div>
    </div>
    {#each $info.mapForContext(ctx) as _, newIndex}
        <MapperRow {state} {ctx} {newIndex} />
    {/each}
</div>

{#if unused.length > 0}
    <div class="alert alert-warning" in:slide out:slide>
        {unusedMsg}
        <ul>
            {#each unused as entry}
                <li>{entry}</li>
            {/each}
        </ul>
    </div>
{/if}
