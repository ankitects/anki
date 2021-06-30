<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import NotetypeSelector from "./NotetypeSelector.svelte";
    import Mapper from "./Mapper.svelte";
    import { ChangeNotetypeState, MapContext } from "./lib";
    import marked from "marked";

    export let state: ChangeNotetypeState;
    let info = state.info;
</script>

<NotetypeSelector {state} />

<h5>{tr.changeNotetypeFields()}</h5>

<Mapper {state} ctx={MapContext.Field} />

<h5>{tr.changeNotetypeTemplates()}</h5>

{#if $info.templates}
    <Mapper {state} ctx={MapContext.Template} />
{:else}
    <div>{@html marked(tr.changeNotetypeToFromCloze())}</div>
{/if}

<style>
    h5 {
        margin-top: 1em;
    }
</style>
