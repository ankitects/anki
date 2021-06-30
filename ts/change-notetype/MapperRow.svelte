<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ChangeNotetypeState, MapContext } from "./lib";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;
    export let newIndex: number;

    let info = state.info;

    function onChange(evt: Event) {
        const oldIdx = parseInt((evt.target as HTMLSelectElement).value, 10);
        state.setOldIndex(ctx, newIndex, oldIdx);
    }
</script>

<div class="row">
    <div class="col">
        <!-- svelte-ignore a11y-no-onchange -->
        <select
            value={$info.getOldIndex(ctx, newIndex)}
            class="form-select"
            on:change={onChange}
        >
            {#each $info.getOldNamesIncludingNothing(ctx) as name, idx}
                <option value={idx}>{name}</option>
            {/each}
        </select>
    </div>
    <div class="col align-self-center">
        {$info.getNewName(ctx, newIndex)}
    </div>
</div>
