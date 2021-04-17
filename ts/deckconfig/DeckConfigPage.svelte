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

    .inner {
        padding: 0.5em;
    }

    :global(input, select) {
        font-size: 16px;
    }
</style>

<div id="modal">
    <!-- filled in later-->
</div>

<div class="outer">
    <div class="inner">
        <ConfigSelector {state} />

        <ConfigEditor {state} />
    </div>
</div>
