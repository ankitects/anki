<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigSelector from "./ConfigSelector.svelte";
    import ConfigEditor from "./ConfigEditor.svelte";
    import type { DeckConfigState } from "./lib";
    import { onMount, onDestroy } from "svelte";
    import { registerShortcut } from "lib/shortcuts";

    export let state: DeckConfigState;

    onMount(() => {
        onDestroy(registerShortcut(() => state.save(false), "Control+Enter"));
    });
</script>

<style lang="scss">
    .editor {
        // without this, the initial viewport can be wrong
        overflow-x: hidden;
    }
</style>

<div>
    <div id="modal">
        <!-- filled in later-->
    </div>

    <ConfigSelector {state} />

    <div class="editor">
        <ConfigEditor {state} />
    </div>
</div>
