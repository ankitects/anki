<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";

    import { noop } from "../lib/functional";
    import { Notes, notes, Notetypes, notetypes } from "../lib/proto";
    import NoteEditor from "./NoteEditor.svelte";
    import { CreatorToolbar } from "./notetype-toolbar";
    import StickyBadge from "./StickyBadge.svelte";

    export let uiResolve: () => void;

    // TODO reimplement togglyStickyAll shortcut on Shift+F9

    let notetype: Notetypes.Notetype;
    let note: Notes.Note;

    const stickiedContents: string[] = [];

    async function setNoteTypeId(ntid: number): Promise<void> {
        const notetypeId = Notetypes.NotetypeId.create({ ntid });

        const nextNotetype = await notetypes.getNotetype(notetypeId);
        const nextNote = await notes.newNote(notetypeId);

        for (let index = 0; index < nextNote.fields.length; index++) {
            if (nextNotetype.fields[index].config!.sticky) {
                nextNote.fields[index] = stickiedContents[index];
            }
        }

        [notetype, note] = [nextNotetype, nextNote];
    }

    Object.assign(globalThis, {
        setNoteTypeId,
        setNoteId: noop,
        getNoteId: () => 0,
    });

    onMount(uiResolve);

    function changeNotetype() {
        console.log(note);
    }

    function isStickyActive(index: number): boolean {
        return notetype.fields[index].config!.sticky;
    }

    function contentUpdate({
        detail: { index, content },
    }: CustomEvent<{ index: number; content: string }>): void {
        stickiedContents[index] = content;
    }
</script>

{#if notetype}
    <CreatorToolbar {notetype} on:notetypechange={changeNotetype} />
{/if}

<NoteEditor {notetype} {note} on:contentupdate={contentUpdate}>
    <svelte:fragment slot="field-state" let:index>
        <StickyBadge active={isStickyActive(index)} {index} />
    </svelte:fragment>
</NoteEditor>
