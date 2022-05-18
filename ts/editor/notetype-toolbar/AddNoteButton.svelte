<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import Shortcut from "../../components/Shortcut.svelte";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { addNoteIcon } from "./icons";
    import TextButton from "./TextButton.svelte";

    const dispatch = createEventDispatcher();

    function addNote() {
        dispatch("noteadd");
    }

    const keyCombination = "Control+Enter";
</script>

<div
    class="add-note-button"
    title="{tr.addingAddNote()} ({getPlatformString(keyCombination)})"
    on:mousedown|preventDefault
    on:click={addNote}
>
    <TextButton>
        <span slot="label">{tr.addingAddNote()}</span>
        <svelte:fragment slot="icon">{@html addNoteIcon}</svelte:fragment>
    </TextButton>
</div>

<Shortcut {keyCombination} on:action={addNote} />
