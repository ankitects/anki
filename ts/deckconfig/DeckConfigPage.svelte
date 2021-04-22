<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigSelector from "./ConfigSelector.svelte";
    import ConfigEditor from "./ConfigEditor.svelte";
    import type { DeckConfigState } from "./lib";

    export let state: DeckConfigState;
</script>

<style>
    .outer {
        display: flex;
        justify-content: center;
    }

    :global(.width-limited) {
        width: 40em;
        padding: 0.5em;
    }
</style>

<div id="modal">
    <!-- filled in later-->
</div>

<ConfigSelector {state} />

<div class="outer">
    <div class="width-limited">
        <ConfigEditor {state} />
    </div>
</div>
