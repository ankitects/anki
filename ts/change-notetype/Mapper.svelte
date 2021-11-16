<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import Col from "../components/Col.svelte";
    import MapperRow from "./MapperRow.svelte";
    import * as tr from "../lib/ftl";
    import { ChangeNotetypeState, MapContext } from "./lib";
    import { slide } from "svelte/transition";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;

    let info = state.info;

    $: unused = $info.unusedItems(ctx);
    $: unusedMsg =
        ctx === MapContext.Field
            ? tr.changeNotetypeWillDiscardContent()
            : tr.changeNotetypeWillDiscardCards();
</script>

<Container --gutter-inline="0.5rem" --gutter-block="0.1rem">
    <Row --cols={2}>
        <Col --col-size={1}><b>{tr.changeNotetypeCurrent()}</b></Col>
        <Col --col-size={1}><b>{tr.changeNotetypeNew()}</b></Col>
    </Row>

    {#each $info.mapForContext(ctx) as _, newIndex}
        <MapperRow {state} {ctx} {newIndex} />
    {/each}
</Container>

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
