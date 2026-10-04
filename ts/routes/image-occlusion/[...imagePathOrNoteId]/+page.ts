// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { get } from "svelte/store";

import { addOrUpdateNote, type ImageOcclusionSaveResult } from "../add-or-update-note.svelte";
import type { IOMode } from "../lib";
import { exportShapesToClozeDeletions } from "../shapes/to-cloze";
import { hideAllGuessOne } from "../store";
import type { PageLoad } from "./$types";

async function save(): Promise<ImageOcclusionSaveResult> {
    return await addOrUpdateNote(globalThis["anki"].imageOcclusion.mode, get(hideAllGuessOne));
}

export const load = (async ({ params }) => {
    let mode: IOMode;
    if (/^\d+/.test(params.imagePathOrNoteId)) {
        mode = { kind: "edit", noteId: BigInt(params.imagePathOrNoteId) };
    } else {
        mode = { kind: "add", imagePath: params.imagePathOrNoteId, notetypeId: 0n };
    }

    // for adding note from mobile devices
    globalThis.anki = globalThis.anki || {};
    globalThis.anki.imageOcclusion = {
        mode,
        save,
        addNote: save,
        hasMasks: () => exportShapesToClozeDeletions(false).noteCount > 0,
        getMaskCount: () => exportShapesToClozeDeletions(false).noteCount,
    };

    return {
        mode,
    };
}) satisfies PageLoad;
