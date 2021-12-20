<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import { writable } from "svelte/store";

    const active = writable(false);
    export const togglePreviewButtonState = (state: boolean) => active.set(state);
</script>

<script lang="ts">
    import { bridgeCommand } from "../lib/bridgecommand";
    import * as tr from "../lib/ftl";
    import { withButton } from "../components/helpers";

    import WithShortcut from "../components/WithShortcut.svelte";
    import LabelButton from "../components/LabelButton.svelte";
</script>

<WithShortcut shortcut={"Control+Shift+P"} let:createShortcut let:shortcutLabel>
    <LabelButton
        tooltip={tr.browsingPreviewSelectedCard({ val: shortcutLabel })}
        active={$active}
        on:click={() => bridgeCommand("preview")}
        on:mount={withButton(createShortcut)}
    >
        {tr.actionsPreview()}
    </LabelButton>
</WithShortcut>
