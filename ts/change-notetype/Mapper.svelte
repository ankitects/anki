<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import Col from "../components/Col.svelte";
    import Badge from "../components/Badge.svelte";
    import MapperRow from "./MapperRow.svelte";
    import * as tr from "../lib/ftl";
    import { ChangeNotetypeState, MapContext } from "./lib";
    import { plusIcon, minusIcon } from "./icons";
    import { slide } from "svelte/transition";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;

    let info = state.info;

    $: unused = $info.unusedItems(ctx);
    $: unusedMsg =
        ctx === MapContext.Field
            ? tr.changeNotetypeWillDiscardContent()
            : tr.changeNotetypeWillDiscardCards();

    let maxItems: number = 4;
    let collapsed: boolean = true;
    $: collapseMsg = collapsed
        ? tr.changeNotetypeExpand()
        : tr.changeNotetypeCollapse();
    $: icon = collapsed ? plusIcon : minusIcon;
</script>

{#if unused.length > 0}
    <div class="alert alert-warning" in:slide out:slide>
        {#if unused.length > maxItems}
            <div class="clickable" on:click={() => collapsed = !collapsed}>
                <Badge iconSize={80}>
                    {@html icon}
                </Badge>
                {collapseMsg}
            </div>
        {/if}
        {unusedMsg}
        <ul>
            {#if collapsed}
                {#each unused.slice(0, maxItems) as entry}
                    <li>{entry}</li>
                {/each}
                {#if unused.length > maxItems}
                    <div class="clickable" on:click={() => collapsed = !collapsed}>
                        +{unused.length - maxItems}
                    </div>
                {/if}
            {:else}
                {#each unused as entry}
                    <li>{entry}</li>
                {/each}
            {/if}
        </ul>
    </div>
{/if}

<Container --gutter-inline="0.5rem" --gutter-block="0.1rem">
    <Row --cols={2}>
        <Col --col-size={1}><b>{tr.changeNotetypeCurrent()}</b></Col>
        <Col --col-size={1}><b>{tr.changeNotetypeNew()}</b></Col>
    </Row>

    {#each $info.mapForContext(ctx) as _, newIndex}
        <MapperRow {state} {ctx} {newIndex} />
    {/each}
</Container>

<style lang="scss">
    .clickable {
        font-weight: bold;
        cursor: pointer;
    }
</style>
