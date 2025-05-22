<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import { registerShortcut } from "@tslib/shortcuts";
    import { onDestroy, onMount } from "svelte";

    import type { NoteEditorAPI } from "./NoteEditor.svelte";
    import NoteEditor from "./NoteEditor.svelte";
    import StickyBadge from "./StickyBadge.svelte";

    const api: Partial<NoteEditorAPI> = {};
    let noteEditor: NoteEditor;

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

<NoteEditor bind:this={noteEditor} {api}>
    <svelte:fragment slot="field-state" let:index let:show>
        <StickyBadge bind:active={stickies[index]} {index} {show} />
    </svelte:fragment>
</NoteEditor>
