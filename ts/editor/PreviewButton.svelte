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
    import Button from "../components/IconButton.svelte";
    import Shortcut from "../components/Shortcut.svelte";
    import { bridgeCommand } from "../lib/bridgecommand";
    import * as tr from "../lib/ftl";
    import { getPlatformString } from "../lib/shortcuts";

    const keyCombination = "Control+Shift+P";
    function preview(): void {
        bridgeCommand("preview");
    }
</script>

<Button
    tooltip={tr.browsingPreviewSelectedCard({ val: getPlatformString(keyCombination) })}
    active={$active}
    on:click={preview}
>
    {tr.actionsPreview()}
</Button>

<Shortcut keyCombination="Control+Shift+P" on:action={preview} />
