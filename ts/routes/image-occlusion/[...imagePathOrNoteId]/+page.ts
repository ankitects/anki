// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { IOMode } from "../lib";
import type { PageLoad } from "./$types";

export const load = (async ({ params }) => {
    let mode: IOMode;
    if (/^\d+/.test(params.imagePathOrNoteId)) {
        mode = { kind: "edit", noteId: Number(params.imagePathOrNoteId) };
    } else {
        mode = { kind: "add", imagePath: params.imagePathOrNoteId, notetypeId: 0 };
    }
    return {
        mode,
    };
}) satisfies PageLoad;
