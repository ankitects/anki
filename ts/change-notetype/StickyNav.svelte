<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../lib/ftl";
    import Badge from "../components/Badge.svelte";
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import Col from "../components/Col.svelte";
    import { exclamationIcon } from "./icons";
    import { ChangeNotetypeState, MapContext } from "./lib";
    import StickyContainer from "../components/StickyContainer.svelte";
    import { plusIcon, minusIcon } from "./icons";
    import { slide } from "svelte/transition";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;

    $: info = state.info;

    let heading: string =
        ctx === MapContext.Field
            ? tr.changeNotetypeFields()
            : tr.changeNotetypeTemplates();

    $: unused = $info.unusedItems(ctx);
    $: unusedMsg =
        ctx === MapContext.Field
            ? tr.changeNotetypeWillDiscardContent()
            : tr.changeNotetypeWillDiscardCards();

    let maxItems: number = 3;
    let collapsed: boolean = true;
    $: collapseMsg = collapsed
        ? tr.changeNotetypeExpand()
        : tr.changeNotetypeCollapse();
    $: icon = collapsed ? plusIcon : minusIcon;
</script>

<StickyContainer
    --sticky-bg={"var(--frame-bg)"}
    --sticky-border="var(--window-bg)"
    --sticky-borders="0 0 1px"
>
    <h1>
        {heading}
        {#if unused.length > 0}
            <Badge iconSize={80}>
                {@html exclamationIcon}
            </Badge>
        {/if}
    </h1>

    {#if unused.length > 0}
        <div class="alert alert-warning" in:slide out:slide>
            {#if unused.length > maxItems}
                <div class="clickable" on:click={() => (collapsed = !collapsed)}>
                    <Badge iconSize={80}>
                        {@html icon}
                    </Badge>
                    {collapseMsg}
                </div>
            {/if}
            {unusedMsg}
            {#if collapsed}
                <div>
                    {unused.slice(0, maxItems).join(", ")}
                    {#if unused.length > maxItems}
                        ... (+{unused.length - maxItems})
                    {/if}
                </div>
            {:else}
                <ul>
                    {#each unused as entry}
                        <li>{entry}</li>
                    {/each}
                </ul>
            {/if}
        </div>
    {/if}
    {#if $info.templates}
        <Container --gutter-inline="0.5rem" --gutter-block="0.2rem">
            <Row --cols={2}>
                <Col --col-size={1}><b>{tr.changeNotetypeCurrent()}</b></Col>
                <Col --col-size={1}><b>{tr.changeNotetypeNew()}</b></Col>
            </Row>
        </Container>
    {/if}
</StickyContainer>

<style lang="scss">
    h1 {
        padding-top: 0.5em;
    }
    .clickable {
        cursor: pointer;
        font-weight: bold;
    }
</style>
