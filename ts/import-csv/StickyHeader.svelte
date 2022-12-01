<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { getPlatformString } from "@tslib/shortcuts";

    import LabelButton from "../components/LabelButton.svelte";
    import Shortcut from "../components/Shortcut.svelte";

    export let path: string;
    export let onImport: () => void;

    const keyCombination = "Control+Enter";

    function basename(path: String): String {
        return path.split(/[\\/]/).pop()!;
    }
</script>

<div class="sticky-header d-flex flex-row justify-content-between">
    <div class="filename">{basename(path)}</div>
    <div class="accept">
        <LabelButton
            primary
            tooltip={getPlatformString(keyCombination)}
            on:click={onImport}
            --border-left-radius="5px"
            --border-right-radius="5px"
        >
            <div class="import">{tr.actionsImport()}</div>
        </LabelButton>
        <Shortcut {keyCombination} on:action={onImport} />
    </div>
</div>

<style lang="scss">
    .sticky-header {
        position: sticky;
        top: 0;
        left: 0;
        right: 0;
        z-index: 10;

        margin: 0;
        padding: 0.5rem;

        background: var(--canvas);
        border-bottom: 1px solid var(--border);

        .import {
            margin-inline: 0.75rem;
        }
    }
</style>
