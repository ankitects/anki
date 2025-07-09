<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { getPlatformString } from "@tslib/shortcuts";
    import Shortcut from "$lib/components/Shortcut.svelte";
    import ActionButton from "./ActionButton.svelte";

    export let onClose: () => void;
    const closeKeyCombination = "Control+Enter";
</script>

<ActionButton onClick={onClose} tooltip={getPlatformString(closeKeyCombination)}>
    {tr.actionsClose()}
    <Shortcut keyCombination={closeKeyCombination} on:action={onClose} />
</ActionButton>
