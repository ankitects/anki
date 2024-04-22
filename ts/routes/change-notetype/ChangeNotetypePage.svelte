<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { renderMarkdown } from "@tslib/helpers";

    import Container from "$lib/components/Container.svelte";
    import Row from "$lib/components/Row.svelte";
    import StickyContainer from "$lib/components/StickyContainer.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";

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

<Container breakpoint="sm" --gutter-inline="0.25rem" --gutter-block="0.75rem">
    <Row --cols={2}>
        <TitledContainer title={tr.changeNotetypeFields()}>
            <Row>
                <StickyHeader {state} ctx={MapContext.Field} --z-index="2" />
                <Mapper {state} ctx={MapContext.Field} />
            </Row>
        </TitledContainer>
    </Row>
    <Row --cols={2}>
        <TitledContainer title={tr.changeNotetypeTemplates()}>
            <Row>
                <StickyHeader {state} ctx={MapContext.Template} --z-index="2" />
                {#if $info.templates}
                    <Mapper {state} ctx={MapContext.Template} />
                {:else}
                    <div>
                        {@html renderMarkdown(tr.changeNotetypeToFromCloze())}
                    </div>
                {/if}
            </Row>
        </TitledContainer>
    </Row>
</Container>
