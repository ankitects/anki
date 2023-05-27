// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";
import { get } from "svelte/store";

import type { Collection } from "../lib/proto";
import type { IOMode } from "./lib";
import { addImageOcclusionNote, updateImageOcclusionNote } from "./lib";
import { exportShapesToClozeDeletions } from "./shapes/to-cloze";
import { notesDataStore, tagsWritable } from "./store";
import Toast from "./Toast.svelte";

export const addOrUpdateNote = async function(
    mode: IOMode,
    occludeInactive: boolean,
): Promise<void> {
    const { clozes: occlusionCloze, noteCount } = exportShapesToClozeDeletions(occludeInactive);
    if (noteCount === 0) {
        return;
    }

    const fieldsData: { id: string; title: string; divValue: string; textareaValue: string }[] = get(notesDataStore);
    const tags = get(tagsWritable);
    let header = fieldsData[0].textareaValue;
    let backExtra = fieldsData[1].textareaValue;

    header = header ? `<div>${header}</div>` : "";
    backExtra = header ? `<div>${backExtra}</div>` : "";

    if (mode.kind == "edit") {
        const result = await updateImageOcclusionNote(
            mode.noteId,
            occlusionCloze,
            header,
            backExtra,
            tags,
        );
        showResult(mode.noteId, result, noteCount);
    } else {
        const result = await addImageOcclusionNote(
            mode.notetypeId,
            mode.imagePath,
            occlusionCloze,
            header,
            backExtra,
            tags,
        );
        showResult(null, result, noteCount);
    }
};

// show toast message
const showResult = (noteId: number | null, result: Collection.OpChanges, count: number) => {
    const toastComponent = new Toast({
        target: document.body,
        props: {
            message: "",
            type: "error",
        },
    });

    if (result.note) {
        const msg = noteId ? tr.browsingCardsUpdated({ count: count }) : tr.importingCardsAdded({ count: count });
        toastComponent.$set({ message: msg, type: "success", showToast: true });
    } else {
        const msg = tr.notetypesErrorGeneratingCloze();
        toastComponent.$set({ message: msg, showToast: true });
    }
};
