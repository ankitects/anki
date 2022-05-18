<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import {
        abortable,
        Cards,
        cards,
        Notes,
        notes,
        Notetypes,
        notetypes,
    } from "../lib/proto";
    import NoteEditor from "./NoteEditor.svelte";
    import { NotetypeToolbar } from "./notetype-toolbar";

    let notetype: Notetypes.Notetype;
    let note: Notes.Note;
    let card: Cards.Card;

    let noteId: number;
    let handle: AbortController;

    async function setCardId(cid: number): Promise<void> {
        // Abort previous fetch (if it is still in process)
        handle?.abort();

        handle = abortable();
        const nextCard = await cards.getCard(Cards.CardId.create({ cid }));
        const nid = nextCard.noteId;

        handle = abortable();
        const nextNote = await notes.getNote(Notes.NoteId.create({ nid }));
        const ntid = nextNote.notetypeId;

        handle = abortable();
        const nextNotetype = await notetypes.getNotetype(
            Notetypes.NotetypeId.create({ ntid }),
        );

        [notetype, note, noteId, card] = [nextNotetype, nextNote, nid, nextCard];
    }

    function getNoteId(): number {
        return noteId;
    }

    function changeNotetype({ detail: notetypeId }) {
        /* TODO will need to display ChangeNotetype as modal ? */
        /* notetypes.changeNotetype(Notetypes.ChangeNotetypeRequest.create({ })); */
    }

    Object.assign(globalThis, { setCardId, getNoteId });
</script>

{#if notetype && card}
    <NotetypeToolbar {notetype} {card} on:notetypechange={changeNotetype} />
{/if}

<NoteEditor {notetype} {note}>
    <slot name="notetypeButtons" slot="notetypeButtons" />
</NoteEditor>
