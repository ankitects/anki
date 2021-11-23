<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../lib/ftl";
    import marked from "marked";
    import { ChangeNotetypeState, MapContext } from "./lib";
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import Col from "../components/Col.svelte";
    import NotetypeSelector from "./NotetypeSelector.svelte";
    import StickyNav from "./StickyNav.svelte";
    import Mapper from "./Mapper.svelte";
    import Spacer from "../components/Spacer.svelte";

    export let state: ChangeNotetypeState;
    $: info = state.info;
    let offset: number;
</script>

<div bind:offsetHeight={offset}>
    <NotetypeSelector {state} />
    <Spacer --height="1em" />
</div>

<div id="scrollArea" style="--offset: {offset}px">
    <Row class="gx-0" --cols={2}>
        <Col --col-size={1} breakpoint="md">
            <Container>
                <StickyNav {state} ctx={MapContext.Field} />
                <Mapper {state} ctx={MapContext.Field} />
            </Container>
        </Col>
        <Col --col-size={1} breakpoint="md">
            <Container>
                <StickyNav {state} ctx={MapContext.Template} />
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
        background: var(--pane-bg);
        height: calc(100% - var(--offset));
        border: 1px solid var(--medium-border);
        border-radius: 0.25rem;
    }
</style>
