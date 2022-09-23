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

    let oldIndex = $info.getOldIndex(ctx, newIndex);

    let options = $info.getOldNamesIncludingNothing(ctx);
    $: state.setOldIndex(ctx, newIndex, oldIndex);
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        <!-- svelte-ignore a11y-no-onchange -->
        <Select current={options[oldIndex]}>
            {#each  options as name, idx}
                <SelectOption on:select={() => (oldIndex = idx)}>{name}</SelectOption>
            {/each}
        </Select>
    </Col>
    <Col --col-size={1}>
        {$info.getNewName(ctx, newIndex)}
    </Col>
</Row>
