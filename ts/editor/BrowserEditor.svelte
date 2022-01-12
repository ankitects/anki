<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import OldEditorAdapter from "../editor/OldEditorAdapter.svelte";
    import type { NoteEditorAPI } from "../editor/OldEditorAdapter.svelte";

    const api: Partial<NoteEditorAPI> = {};
    let noteEditor: OldEditorAdapter;

    export let uiResolve: (api: NoteEditorAPI) => void;

    $: if (noteEditor) {
        uiResolve(api as NoteEditorAPI);
    }
</script>

<OldEditorAdapter bind:this={noteEditor} {api} />
