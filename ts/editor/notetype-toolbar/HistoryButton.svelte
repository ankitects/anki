<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Shortcut from "../../components/Shortcut.svelte";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { historyIcon } from "./icons";
    import TextButton from "./TextButton.svelte";

    const keyCombination = "Control+Shift+H";
</script>

<div
    class="history-button"
    title="{tr.addingHistory()} ({getPlatformString(keyCombination)})"
    on:mousedown|preventDefault
>
    <TextButton>
        <span slot="label">{tr.addingHistory()}</span>
        <svelte:fragment slot="icon">{@html historyIcon}</svelte:fragment>
    </TextButton>
</div>

<Shortcut {keyCombination} on:action={console.log} />
