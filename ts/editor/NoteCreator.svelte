<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount, onDestroy } from "svelte";
    import { bridgeCommand } from "../lib/bridgecommand";
    import { registerShortcut } from "../lib/shortcuts";
    import StickyBadge from "./StickyBadge.svelte";
    import OldEditorAdapter from "./OldEditorAdapter.svelte";
    import type { NoteEditorAPI } from "./OldEditorAdapter.svelte";

    const api: Partial<NoteEditorAPI> = {};
    let noteEditor: OldEditorAdapter;

    export let uiResolve: (api: NoteEditorAPI) => void;

    $: if (noteEditor) {
        uiResolve(api as NoteEditorAPI);
    }

    let stickies: boolean[] = [];

    function setSticky(stckies: boolean[]): void {
        stickies = stckies;
    }

    function toggleStickyAll(): void {
        bridgeCommand("toggleStickyAll", (values: boolean[]) => (stickies = values));
    }

    let deregisterSticky: () => void;
    export function activateStickyShortcuts() {
        deregisterSticky = registerShortcut(toggleStickyAll, "Shift+F9");
    }

    onMount(() => {
        Object.assign(globalThis, {
            setSticky,
        });
    });

    onDestroy(() => deregisterSticky);
</script>

<OldEditorAdapter bind:this={noteEditor} {api}>
    <svelte:fragment slot="field-state" let:index>
        <StickyBadge active={stickies[index]} {index} />
    </svelte:fragment>
</OldEditorAdapter>
