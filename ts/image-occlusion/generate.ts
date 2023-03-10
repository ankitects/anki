// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";
import { fabric } from "fabric";
import { get } from "svelte/store";

import type { Collection } from "../lib/proto";
import { addImageOcclusionNote, updateImageOcclusionNote } from "./lib";
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

// Defines the number of fraction digits to use when serializing object values
fabric.Object.NUM_FRACTION_DIGITS = 2;

export function generate(hideInactive: boolean): { occlusionCloze: string; noteCount: number } {
    const canvas = globalThis.canvas;
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
            clozeData += getGroupCloze(object, index, hideInactive);
        } else {
            clozeData += getCloze(object, index, null, hideInactive);
        }
    });

    occlusionCloze += clozeData;
    return { occlusionCloze, noteCount };
}

const getCloze = (object, index, relativePos, hideInactive): string => {
    const obJson = object.toJSON();
    let clozeData = "";

    // generate cloze data in form of
    // {{c1::image-occlusion:rect:top=100:left=100:width=100:height=100}}
    Object.keys(obJson).forEach(function(key) {
        if (divData.includes(key)) {
            if (key === "type") {
                clozeData += `:${obJson[key]}`;

                if (obJson[key] === "ellipse") {
                    clozeData += `:rx=${obJson.rx.toFixed(2)}:ry=${obJson.ry.toFixed(2)}`;
                }

                if (obJson[key] === "polygon") {
                    const points = obJson.points;
                    let pnts = "";
                    points.forEach((point: { x: number; y: number }) => {
                        pnts += point.x.toFixed(2) + "," + point.y.toFixed(2) + " ";
                    });
                    clozeData += `:points=${pnts.trim()}`;
                }
            } else if (relativePos && key === "top") {
                clozeData += `:top=${relativePos.top}`;
            } else if (relativePos && key === "left") {
                clozeData += `:left=${relativePos.left}`;
            } else {
                clozeData += `:${key}=${obJson[key]}`;
            }
        }
    });

    clozeData += `:hideinactive=${hideInactive}`;
    clozeData = `{{c${index + 1}::image-occlusion${clozeData}}}\n`;
    return clozeData;
};

const getGroupCloze = (group, index, hideInactive): string => {
    let clozeData = "";
    const objects = group._objects;

    objects.forEach((object) => {
        const { top, left } = getObjectPositionInGroup(group, object);
        clozeData += getCloze(object, index, { top, left }, hideInactive);
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

export const saveImageNotes = async function(
    imagePath: string,
    noteId: number,
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

    if (noteId) {
        const result = await updateImageOcclusionNote(
            noteId,
            occlusionCloze,
            header,
            backExtra,
            tags,
        );
        showResult(noteId, result, noteCount);
    } else {
        const result = await addImageOcclusionNote(
            imagePath,
            occlusionCloze,
            header,
            backExtra,
            tags,
        );
        showResult(noteId, result, noteCount);
    }
};

// show toast message
const showResult = (noteId: number, result: Collection.OpChanges, count: number) => {
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
