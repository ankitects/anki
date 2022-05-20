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

    function changeNotetype(notetypeId: number): void {
        note.fields;
    }

    let deckId: number = 1;

    function changeDeck(did: number): void {
        deckId = did;
    }

    let addedNoteIds: number[] = [];

    async function addNote(): Promise<void> {
        const result = await notes.addNote(
            Notes.AddNoteRequest.create({ note, deckId }),
        );

        addedNoteIds.push(result.noteId);
        addedNoteIds = addedNoteIds;
    }

    function isStickyActive(index: number): boolean {
        return notetype.fields[index].config!.sticky;
    }

    function contentUpdate({
        detail: { index, content },
    }: CustomEvent<{ index: number; content: string }>): void {
        stickiedContents[index] = content;
        note.fields[index] = content;
    }
</script>

<div class="note-creator">
    {#if notetype}
        <CreatorToolbar
            {notetype}
            {deckId}
            {addedNoteIds}
            on:notetypechange={({ detail: notetypeId }) => changeNotetype(notetypeId)}
            on:deckchange={({ detail: deckId }) => changeDeck(deckId)}
            on:noteadd={addNote}
        />
    {/if}

    <NoteEditor
        fields={notetype?.fields ?? []}
        contents={note?.fields ?? []}
        tags={note?.tags ?? []}
        on:contentupdate={contentUpdate}
    >
        <svelte:fragment slot="field-state" let:index>
            <StickyBadge active={isStickyActive(index)} {index} />
        </svelte:fragment>
    </NoteEditor>
</div>

<style lang="scss">
    .note-creator {
        display: flex;
        flex-direction: column;
        height: 100%;
    }
</style>
