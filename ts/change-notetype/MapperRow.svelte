<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import Select from "../components/Select.svelte";
    import SelectOption from "../components/SelectOption.svelte";
    import type { ChangeNotetypeState, MapContext } from "./lib";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;
    export let newIndex: number;

    const info = state.info;
    $: oldIndex = $info.getOldIndex(ctx, newIndex);

    function onChange(evt: CustomEvent) {
        oldIndex = evt.detail.value;
        state.setOldIndex(ctx, newIndex, oldIndex);
    }

    $: label = $info.getOldNamesIncludingNothing(ctx)[oldIndex];
</script>

<Row>
    <Col>
        <Select value={oldIndex} {label} on:change={onChange}>
            {#each $info.getOldNamesIncludingNothing(ctx) as name, idx}
                <SelectOption value={idx}>{name}</SelectOption>
            {/each}
        </Select>
    </Col>
    <Col>
        {$info.getNewName(ctx, newIndex)}
    </Col>
</Row>
