<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../lib/ftl";
    import Row from "../components/Row.svelte";
    import Col from "../components/Col.svelte";
    import type { ChangeNotetypeState, MapContext } from "./lib";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;
    export let newIndex: number;

    $: info = state.info;

    let oldNames: string[];
    let current: string;
    $: {
        oldNames = $info.getOldNamesIncludingNothing(ctx);
        current = oldNames[newIndex] || tr.changeNotetypeNothing();
    }

    // optimization for big notetypes
    let active: boolean = false;
    function activate(evt: Event) {
        active = true;
    }

    function onChange(evt: Event) {
        const oldIdx = parseInt((evt.target as HTMLSelectElement).value, 10);
        state.setOldIndex(ctx, newIndex, oldIdx);
    }
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        <!-- svelte-ignore a11y-no-onchange -->
        <select
            value={active ? $info.getOldIndex(ctx, newIndex) : 0}
            class="form-select"
            on:focusin={activate}
            on:change={onChange}
        >
            {#if active}
                {#each oldNames as name, idx}
                    <option value={idx}>{name}</option>
                {/each}
            {:else}
                <option value={0}>{current}</option>
            {/if}
        </select>
    </Col>
    <Col --col-size={1}>
        {$info.getNewName(ctx, newIndex)}
    </Col>
</Row>
