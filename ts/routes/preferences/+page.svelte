<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import "../deck-options/deck-options-base.scss";
    import LabItem from "./LabItem.svelte";
    import type { PreferenceStore } from "$lib/sveltelib/preferences";
    import { ExperimentalFeatureFlag } from "@generated/anki/config_pb";

    export let data: { labPerfs: PreferenceStore<any> };
    const labPerfs = data.labPerfs;

    // Use the systems font size for the preferences page to align with the other menus
    // This is set in standard_css which is called as part of add_dynamic_styling_and_props_then_show in webview.py
    document.documentElement.classList.add("system-font-size");
</script>

<div class="container">
    <div class="alert alert-warning mb-0">
        <b>⚠️ Experimental Features</b>
        <br />
        These features may change, break, or be removed without notice. Add-on compatibility
        is not guaranteed. Use at your own risk.
    </div>

    <div class="lab-grid">
        <LabItem
            title="Note Editor Rework"
            description="Enables an experimental rework of the editor. You can also hold shift while opening the editor to temporarily toggle this option on or off. <a href='https://forums.ankiweb.net/t/experimental-editor-testing/71454'>See&nbsp;forum&nbsp;post</a>"
            key={ExperimentalFeatureFlag.SVELTE_EDITOR}
            {labPerfs}
        ></LabItem>
        <!-- <LabItem
            title="Ping"
            description="Enable this experiment and see an alert every time you load this profile. Used for testing the experiment interface."
            key={ExperimentalFeatureFlag.TEST_FLAG}
            {labPerfs}
        ></LabItem> -->
    </div>
</div>

<style>
    .lab-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1em;
    }

    .container {
        display: flex;
        flex-direction: column;
        gap: 1em;
        margin: 1em;
        align-items: center;
    }

    .alert {
        width: 100%;
    }

    :global(body) {
        background-color: var(--canvas-elevated) !important;
    }
</style>
