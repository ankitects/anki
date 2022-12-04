<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import type { ChangeNotetypeState, MapContext } from "./lib";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;
    export let newIndex: number;

    const info = state.info;

    function onChange(evt: Event) {
        const oldIdx = parseInt((evt.target as HTMLSelectElement).value, 10);
        state.setOldIndex(ctx, newIndex, oldIdx);
    }
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        <select
            value={$info.getOldIndex(ctx, newIndex)}
            class="form-select"
            on:change={onChange}
        >
            {#each $info.getOldNamesIncludingNothing(ctx) as name, idx}
                <option value={idx}>{name}</option>
            {/each}
        </select>
    </Col>
    <Col --col-size={1}>
        {$info.getNewName(ctx, newIndex)}
    </Col>
</Row>
