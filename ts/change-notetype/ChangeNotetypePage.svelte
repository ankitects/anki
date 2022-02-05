<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { marked } from "marked";

    import Col from "../components/Col.svelte";
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import * as tr from "../lib/ftl";
    import { ChangeNotetypeState, MapContext } from "./lib";
    import Mapper from "./Mapper.svelte";
    import NotetypeSelector from "./NotetypeSelector.svelte";
    import StickyHeader from "./StickyHeader.svelte";

    export let state: ChangeNotetypeState;
    $: info = state.info;
    let offset: number;
</script>

<div bind:offsetHeight={offset}>
    <NotetypeSelector {state} />
</div>

<div id="scrollArea" style="--offset: {offset}px; --gutter-inline: 0.25rem;">
    <Row class="gx-0" --cols={2}>
        <Col --col-size={1} breakpoint="md">
            <Container>
                <StickyHeader {state} ctx={MapContext.Field} />
                <Mapper {state} ctx={MapContext.Field} />
            </Container>
        </Col>
        <Col --col-size={1} breakpoint="md">
            <Container>
                <StickyHeader {state} ctx={MapContext.Template} />
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
