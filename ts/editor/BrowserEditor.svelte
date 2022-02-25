<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";

    import ButtonGroupItem from "../components/ButtonGroupItem.svelte";
    import NoteEditingEditor from "./NoteEditingEditor.svelte";
    import PreviewButton from "./PreviewButton.svelte";

    export let uiResolve: () => void;

    onMount(uiResolve);
</script>

<NoteEditingEditor>
    <svelte:fragment slot="notetypeButtons">
        <ButtonGroupItem>
            <PreviewButton />
        </ButtonGroupItem>
    </svelte:fragment>
</NoteEditingEditor>
