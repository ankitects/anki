<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import ScrollArea from "components/ScrollArea.svelte";
    import { marked } from "marked";

    import Page from "../components/Page.svelte";
    import TitledContainer from "../components/TitledContainer.svelte";
    import type { ChangeNotetypeState } from "./lib";
    import { MapContext } from "./lib";
    import Mapper from "./Mapper.svelte";
    import NotetypeSelector from "./NotetypeSelector.svelte";
    import Subheading from "./Subheading.svelte";

    export let state: ChangeNotetypeState;
    $: info = state.info;
</script>

<Page>
    <NotetypeSelector slot="header" {state} />

    <div class="layout">
        <div class="h-100" style:grid-area="fields">
            <TitledContainer title={tr.changeNotetypeFields()}>
                <Subheading {state} ctx={MapContext.Field} />
                <ScrollArea scrollY>
                    <Mapper {state} ctx={MapContext.Field} />
                </ScrollArea>
            </TitledContainer>
        </div>
        <div class="h-100" style:grid-area="templates">
            <TitledContainer title={tr.changeNotetypeTemplates()}>
                <Subheading {state} ctx={MapContext.Template} />
                <ScrollArea scrollY>
                    {#if $info.templates}
                        <Mapper {state} ctx={MapContext.Template} />
                    {:else}
                        <div>{@html marked(tr.changeNotetypeToFromCloze())}</div>
                    {/if}
                </ScrollArea>
            </TitledContainer>
        </div>
    </div>
</Page>

<style lang="scss">
    @use "sass/breakpoints" as bp;
    .layout {
        width: 100%;
        height: 100%;
        display: grid;
        grid-gap: 0.5rem;

        grid-template:
            "fields" 1fr
            "templates" 1fr;

        @include bp.with-breakpoint("md") {
            grid-template: "fields templates" / 1fr 1fr;
            grid-gap: 1rem;
        }
    }

    :global(.row) {
        // rows have negative margins by default
        --bs-gutter-x: 0;
    }
</style>
