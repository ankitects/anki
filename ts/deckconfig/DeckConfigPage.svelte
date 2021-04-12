<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type pb from "anki/backend_proto";
    import ConfigSelector from "./ConfigSelector.svelte";
    import ConfigEditor from "./ConfigEditor.svelte";
    import * as tr from "anki/i18n";
    import type { DeckConfigState } from "./lib";

    export let state: DeckConfigState;

    let selectedConfigId = state.selectedConfigId;

    let selectedConfig: pb.BackendProto.DeckConfig.Config;
    $: {
        selectedConfig = (
            state.allConfigs.find((e) => e.config.id == selectedConfigId)?.config ??
            state.allConfigs[0].config
        ).config as pb.BackendProto.DeckConfig.Config;
    }
    let defaults = state.defaults;
</script>

<style>
    .outer {
        display: flex;
        justify-content: center;
    }

    .inner {
        padding: 0.5em;
    }

    :global(input, select) {
        font-size: 16px;
    }
</style>

<div class="outer">
    <div class="inner">
        <div><b>{tr.actionsOptionsFor({ val: state.deckName })}</b></div>

        <ConfigSelector allConfig={state.allConfigs} bind:selectedConfigId />

        <ConfigEditor config={selectedConfig} {defaults} />
    </div>
</div>
