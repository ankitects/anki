// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";
import type { StaticCanvas } from "fabric";
import { get } from "svelte/store";

import type { Collection } from "../lib/proto";
import type { IOMode } from "./lib";
import { addImageOcclusionNote, updateImageOcclusionNote } from "./lib";
import { xToNormalized, yToNormalized } from "./position";
import { notesDataStore, tagsWritable } from "./store";
import Toast from "./Toast.svelte";
import { makeMaskTransparent } from "./tools/lib";

const divData = [
    "height",
    "left",
    "top",
    "type",
    "width",
];

export function generate(hideInactive: boolean): { occlusionCloze: string; noteCount: number } {
    const canvas = globalThis.canvas as StaticCanvas;
    const canvasObjects = canvas.getObjects();
    if (canvasObjects.length < 1) {
        return { occlusionCloze: "", noteCount: 0 };
    }

    let occlusionCloze = "";
    let clozeData = "";
    let noteCount = 0;

    makeMaskTransparent(canvas, false);

    canvasObjects.forEach((object, index) => {
        const obJson = object.toJSON();
        noteCount++;
        if (obJson.type === "group") {
            clozeData += getGroupCloze(canvas, object, index, hideInactive);
        } else {
            clozeData += getCloze(canvas, object, index, null, hideInactive);
        }
    });

    occlusionCloze += clozeData;
    return { occlusionCloze, noteCount };
}

const getCloze = (canvas: HTMLCanvasElement, object, index, relativePos, hideInactive): string => {
    const obJson = object.toJSON();
    let clozeData = "";

    // generate cloze data in form of
    // {{c1::image-occlusion:rect:top=.1:left=.23:width=.4:height=.5}}
    Object.keys(obJson).forEach(function(key) {
        if (divData.includes(key)) {
            if (key === "type") {
                clozeData += `:${obJson[key]}`;

                if (obJson[key] === "ellipse") {
                    clozeData += `:rx=${xToNormalized(canvas, obJson.rx)}:ry=${yToNormalized(canvas, obJson.ry)}`;
                }

                if (obJson[key] === "polygon") {
                    const points = obJson.points;
                    let pnts = "";
                    points.forEach((point: { x: number; y: number }) => {
                        pnts += xToNormalized(canvas, point.x) + "," + yToNormalized(canvas, point.y) + " ";
                    });
                    clozeData += `:points=${pnts.trim()}`;
                }
            } else if (key === "top") {
                clozeData += `:top=${yToNormalized(canvas, relativePos?.top ?? obJson.top)}`;
            } else if (key === "left") {
                clozeData += `:left=${xToNormalized(canvas, relativePos?.left ?? obJson.left)}`;
            } else if (key === "width") {
                clozeData += `:width=${xToNormalized(canvas, obJson.width)}`;
            } else if (key === "height") {
                clozeData += `:height=${yToNormalized(canvas, obJson.height)}`;
            }
        }
    });

    clozeData += `:hideinactive=${hideInactive}`;
    clozeData = `{{c${index + 1}::image-occlusion${clozeData}}}<br>`;
    return clozeData;
};

const getGroupCloze = (canvas: HTMLCanvasElement, group, index, hideInactive): string => {
    let clozeData = "";
    const objects = group._objects;

    objects.forEach((object) => {
        const { top, left } = getObjectPositionInGroup(group, object);
        clozeData += getCloze(canvas, object, index, { top, left }, hideInactive);
    });

    return clozeData;
};

const getObjectPositionInGroup = (group, object): { top: number; left: number } => {
    let left = object.left + group.left + group.width / 2;
    let top = object.top + group.top + group.height / 2;
    left = left.toFixed(2);
    top = top.toFixed(2);
    return { top, left };
};

export const addOrUpdateNote = async function(
    mode: IOMode,
    hideInactive: boolean,
): Promise<void> {
    const { occlusionCloze, noteCount } = generate(hideInactive);
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
