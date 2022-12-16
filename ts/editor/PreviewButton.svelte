<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import { writable } from "svelte/store";

    const active = writable(false);

    export function togglePreviewButtonState(state: boolean): void {
        active.set(state);
    }

    Object.assign(globalThis, { togglePreviewButtonState });
</script>

<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import * as tr from "@tslib/ftl";
    import { getPlatformString } from "@tslib/shortcuts";

    import LabelButton from "../components/LabelButton.svelte";
    import Shortcut from "../components/Shortcut.svelte";

    const keyCombination = "Control+Shift+P";
    function preview(): void {
        bridgeCommand("preview");
    }
</script>

<LabelButton
    tooltip={tr.browsingPreviewSelectedCard({ val: getPlatformString(keyCombination) })}
    active={$active}
    on:click={preview}
>
    {tr.actionsPreview()}
</LabelButton>

<Shortcut keyCombination="Control+Shift+P" on:action={preview} />
