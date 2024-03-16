<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroupItem from "$lib/components/ButtonGroupItem.svelte";

    import type { NoteEditorAPI } from "./NoteEditor.svelte";
    import NoteEditor from "./NoteEditor.svelte";
    import PreviewButton from "./PreviewButton.svelte";

    const api: Partial<NoteEditorAPI> = {};
    let noteEditor: NoteEditor;

    export let uiResolve: (api: NoteEditorAPI) => void;

    $: if (noteEditor) {
        uiResolve(api as NoteEditorAPI);
    }
</script>

<NoteEditor bind:this={noteEditor} {api}>
    <svelte:fragment slot="notetypeButtons">
        <ButtonGroupItem>
            <PreviewButton />
        </ButtonGroupItem>
    </svelte:fragment>
</NoteEditor>
