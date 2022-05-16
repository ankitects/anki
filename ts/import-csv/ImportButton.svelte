<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../components/ButtonGroup.svelte";
    import LabelButton from "../components/LabelButton.svelte";
    import Shortcut from "../components/Shortcut.svelte";
    import * as tr from "../lib/ftl";
    import { getPlatformString } from "../lib/shortcuts";

    export let onImport: () => void;

    function doImport(): void {
        if (document.activeElement instanceof HTMLElement) {
            document.activeElement.blur();
        }
        onImport();
    }

    const keyCombination = "Control+Enter";
</script>

<ButtonGroup>
    <LabelButton
        theme="primary"
        tooltip={getPlatformString(keyCombination)}
        on:click={doImport}
        --border-left-radius="5px"
        --border-right-radius="5px">{tr.actionsImport()}</LabelButton
    >
    <Shortcut {keyCombination} on:action={doImport} />
</ButtonGroup>
