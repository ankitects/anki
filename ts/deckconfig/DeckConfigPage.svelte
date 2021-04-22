<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigSelector from "./ConfigSelector.svelte";
    import ConfigEditor from "./ConfigEditor.svelte";
    import type { DeckConfigState } from "./lib";
    import { primaryModifierForPlatform } from "sveltelib/shortcuts";

    export let state: DeckConfigState;

    function onKeyDown(evt: KeyboardEvent): void {
        if (
            evt.code === "Enter" &&
            evt.getModifierState(primaryModifierForPlatform())
        ) {
            state.save(false);
        } else {
            console.log(evt.getModifierState(primaryModifierForPlatform()));
            console.log(primaryModifierForPlatform());
        }
    }
</script>

<style lang="scss">
    .editor {
        // without this, the initial viewport can be wrong
        overflow-x: hidden;
    }
</style>

<div on:keydown={onKeyDown}>
    <div id="modal">
        <!-- filled in later-->
    </div>

    <ConfigSelector {state} />

    <div class="editor">
        <ConfigEditor {state} />
    </div>
</div>
