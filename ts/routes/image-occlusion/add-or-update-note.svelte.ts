// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { addImageOcclusionNote, updateImageOcclusionNote } from "@generated/backend";
import * as tr from "@generated/ftl";
import { get } from "svelte/store";

import type { IOAddingMode, IOMode } from "./lib";
import { exportShapesToClozeDeletions } from "./shapes/to-cloze";
import { notesDataStore, tagsWritable } from "./store";
import { showToast } from "./toast-utils.svelte";

export type ImageOcclusionSaveResult =
    | { success: true; kind: "success"; count: number }
    | { success: false; kind: "no-masks"; message: string }
    | { success: false; kind: "error"; error: string; message: string };

export const addOrUpdateNote = async function(
    mode: IOMode,
    occludeInactive: boolean,
): Promise<ImageOcclusionSaveResult> {
    const { clozes: occlusionCloze, noteCount } = exportShapesToClozeDeletions(occludeInactive);
    if (noteCount === 0) {
        const message = tr.notetypesNoOcclusionCreated();
        showToast(message, "error");
        return {
            success: false,
            kind: "no-masks",
            message,
        };
    }

    const fieldsData: { id: string; title: string; divValue: string; textareaValue: string }[] = get(notesDataStore);
    const tags = get(tagsWritable);
    let header = fieldsData[0]?.textareaValue ?? "";
    let backExtra = fieldsData[1]?.textareaValue ?? "";

    header = header ? `<div>${header}</div>` : "";
    backExtra = backExtra ? `<div>${backExtra}</div>` : "";

    try {
        if (mode.kind === "edit") {
            await updateImageOcclusionNote({
                noteId: mode.noteId,
                occlusions: occlusionCloze,
                header,
                backExtra,
                tags,
            });
            showResult(mode.noteId, noteCount);
        } else {
            await addImageOcclusionNote({
                // IOCloningMode is not used on mobile
                notetypeId: BigInt((<IOAddingMode> mode).notetypeId),
                imagePath: (<IOAddingMode> mode).imagePath,
                occlusions: occlusionCloze,
                header,
                backExtra,
                tags,
            });
            showResult(null, noteCount);
        }
        return {
            success: true,
            kind: "success",
            count: noteCount,
        };
    } catch (err: any) {
        const message = err?.message || tr.notetypesErrorGeneratingCloze();
        showToast(message, "error");
        return {
            success: false,
            kind: "error",
            error: String(err),
            message,
        };
    }
};

// show toast message
const showResult = (noteId: bigint | null, count: number) => {
    const message = noteId ? tr.browsingCardsUpdated({ count: count }) : tr.importingCardsAdded({ count: count });
    const type = "success" as "error" | "success";
    showToast(message, type);
};
