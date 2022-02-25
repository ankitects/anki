<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { abortable, Notes, notes, Notetypes, notetypes } from "../lib/proto";
    import NoteEditor from "./NoteEditor.svelte";

    let notetype: Notetypes.Notetype;
    let note: Notes.Note;

    let noteId: number;
    let handle: AbortController;

    async function setNoteId(nid: number): Promise<void> {
        // Abort previous fetch (if it is still in process)
        handle?.abort();

        handle = abortable();
        const nextNote = await notes.getNote(Notes.NoteId.create({ nid }));
        const ntid = nextNote.notetypeId;

        handle = abortable();
        const nextNotetype = await notetypes.getNotetype(
            Notetypes.NotetypeId.create({ ntid }),
        );

        [notetype, note, noteId] = [nextNotetype, nextNote, nid];
    }

    function getNoteId(): number {
        return noteId;
    }

    Object.assign(globalThis, { setNoteId, getNoteId });
</script>

<NoteEditor {notetype} {note}>
    <slot name="notetypeButtons" slot="notetypeButtons" />
</NoteEditor>
