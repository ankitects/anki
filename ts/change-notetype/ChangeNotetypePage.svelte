<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { marked } from "marked";

    import Col from "../components/Col.svelte";
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import StickyContainer from "../components/StickyContainer.svelte";
    import type { ChangeNotetypeState } from "./lib";
    import { MapContext } from "./lib";
    import Mapper from "./Mapper.svelte";
    import NotetypeSelector from "./NotetypeSelector.svelte";
    import StickyHeader from "./StickyHeader.svelte";

    export let state: ChangeNotetypeState;
    $: info = state.info;
    let offset: number;
</script>

<div bind:offsetHeight={offset}>
    <StickyContainer
        --gutter-block="0.1rem"
        --gutter-inline="0.25rem"
        --sticky-borders="0 0 1px"
        --z-index="4"
    >
        <NotetypeSelector {state} />
    </StickyContainer>
</div>

<div id="scrollArea" style="--offset: {offset}px; --gutter-inline: 0.25rem;">
    <Row class="gx-0" --cols={2}>
        <Col --col-size={1} breakpoint="md">
            <Container>
                <StickyHeader {state} ctx={MapContext.Field} --z-index="2" />
                <Mapper {state} ctx={MapContext.Field} />
            </Container>
        </Col>
        <Col --col-size={1} breakpoint="md">
            <Container>
                <StickyHeader {state} ctx={MapContext.Template} --z-index="2" />
                {#if $info.templates}
                    <Mapper {state} ctx={MapContext.Template} />
                {:else}
                    <div>{@html marked(tr.changeNotetypeToFromCloze())}</div>
                {/if}
            </Container>
        </Col>
    </Row>
</div>

<style>
    #scrollArea {
        padding: 0;
        overflow: hidden auto;
        height: calc(100% - var(--offset));
    }
</style>
