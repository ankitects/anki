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
    import ScrollArea from "./ScrollArea.svelte";
    import StickyNav from "./StickyNav.svelte";
    import Mapper from "./Mapper.svelte";
    import Spacer from "../components/Spacer.svelte";

    export let state: ChangeNotetypeState;
    $: info = state.info;
    let offset: number;
</script>

<div bind:clientHeight={offset}>
    <NotetypeSelector {state} />
    <Spacer --height="1em" />
</div>

<ScrollArea --gutter-inline="0.5rem" {offset}>
    <Row --cols={2}>
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
</ScrollArea>
