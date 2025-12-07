// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { OpChanges } from "@generated/anki/collection_pb";
import { addImageOcclusionNote, updateImageOcclusionNote } from "@generated/backend";
import * as tr from "@generated/ftl";
import { get } from "svelte/store";

import { mount } from "svelte";
import type { IOAddingMode, IOMode } from "./lib";
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
    backExtra = backExtra ? `<div>${backExtra}</div>` : "";

    if (mode.kind == "edit") {
        const result = await updateImageOcclusionNote({
            noteId: BigInt(mode.noteId),
            occlusions: occlusionCloze,
            header,
            backExtra,
            tags,
        });
        if (result.note) {
            showResult(mode.noteId, result, noteCount);
        }
    } else {
        const result = await addImageOcclusionNote({
            // IOCloningMode is not used on mobile
            notetypeId: BigInt((<IOAddingMode> mode).notetypeId),
            imagePath: (<IOAddingMode> mode).imagePath,
            occlusions: occlusionCloze,
            header,
            backExtra,
            tags,
        });
        showResult(null, result, noteCount);
    }
};

// show toast message
const showResult = (noteId: number | null, result: OpChanges, count: number) => {
    const props = $state({
        message: noteId ? tr.browsingCardsUpdated({ count: count }) : tr.importingCardsAdded({ count: count }),
        type: "success" as "error" | "success",
        showToast: true,
    });
    mount(Toast, {
        target: document.body,
        props,
    });
};
