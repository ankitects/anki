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

<style lang="scss">
    .editor {
        // without this, the initial viewport can be wrong
        overflow-x: hidden;
    }
</style>

<div id="modal">
    <!-- filled in later-->
</div>

<ConfigSelector {state} />

<div class="editor">
    <ConfigEditor {state} />
</div>
