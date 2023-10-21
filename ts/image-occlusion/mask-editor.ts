// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { protoBase64 } from "@bufbuild/protobuf";
import { getImageForOcclusion, getImageOcclusionNote } from "@tslib/backend";
import * as tr from "@tslib/ftl";
import { fabric } from "fabric";
import type { PanZoom } from "panzoom";
import { get } from "svelte/store";

import { optimumCssSizeForCanvas } from "./canvas-scale";
import { notesDataStore, tagsWritable, zoomResetValue } from "./store";
import Toast from "./Toast.svelte";
import { addShapesToCanvasFromCloze } from "./tools/add-from-cloze";
import { enableSelectable, moveShapeToCanvasBoundaries } from "./tools/lib";
import { modifiedPolygon } from "./tools/tool-polygon";
import { undoStack } from "./tools/tool-undo-redo";
import type { Size } from "./types";

export const setupMaskEditor = async (
    path: string,
    instance: PanZoom,
    onChange: () => void,
): Promise<fabric.Canvas> => {
    const imageData = await getImageForOcclusion({ path });
    const canvas = initCanvas(onChange);

    // get image width and height
    const image = document.getElementById("image") as HTMLImageElement;
    image.src = getImageData(imageData.data!);
    image.onload = function() {
        const size = optimumCssSizeForCanvas({ width: image.width, height: image.height }, containerSize());
        canvas.setWidth(size.width);
        canvas.setHeight(size.height);
        image.height = size.height;
        image.width = size.width;
        setCanvasZoomRatio(canvas, instance);
        undoStack.reset();
    };

    return canvas;
};

export const setupMaskEditorForEdit = async (
    noteId: number,
    instance: PanZoom,
    onChange: () => void,
): Promise<fabric.Canvas> => {
    const clozeNoteResponse = await getImageOcclusionNote({ noteId: BigInt(noteId) });
    const kind = clozeNoteResponse.value?.case;
    if (!kind || kind === "error") {
        new Toast({
            target: document.body,
            props: {
                message: tr.notetypesErrorGettingImagecloze(),
                type: "error",
            },
        }).$set({ showToast: true });
        return;
    }

    const clozeNote = clozeNoteResponse.value.value;
    const canvas = initCanvas(onChange);

    // get image width and height
    const image = document.getElementById("image") as HTMLImageElement;
    image.style.visibility = "hidden";
    image.src = getImageData(clozeNote.imageData!);
    image.onload = function() {
        const size = optimumCssSizeForCanvas({ width: image.width, height: image.height }, containerSize());
        canvas.setWidth(size.width);
        canvas.setHeight(size.height);
        image.height = size.height;
        image.width = size.width;

        setCanvasZoomRatio(canvas, instance);
        addShapesToCanvasFromCloze(canvas, clozeNote.occlusions);
        enableSelectable(canvas, true);
        addClozeNotesToTextEditor(clozeNote.header, clozeNote.backExtra, clozeNote.tags);
        undoStack.reset();
        window.requestAnimationFrame(() => {
            image.style.visibility = "visible";
        });
    };

    return canvas;
};

function initCanvas(onChange: () => void): fabric.Canvas {
    const canvas = new fabric.Canvas("canvas");
    tagsWritable.set([]);
    globalThis.canvas = canvas;
    undoStack.setCanvas(canvas);
    // Disable uniform scaling
    canvas.uniformScaling = false;
    canvas.uniScaleKey = "none";
    moveShapeToCanvasBoundaries(canvas);
    canvas.on("object:modified", (evt) => {
        if (evt.target instanceof fabric.Polygon) {
            modifiedPolygon(canvas, evt.target);
            undoStack.onObjectModified();
        }
        onChange();
    });
    canvas.on("object:removed", onChange);
    return canvas;
}

const getImageData = (imageData): string => {
    const b64encoded = protoBase64.enc(imageData);
    return "data:image/png;base64," + b64encoded;
};

export const setCanvasZoomRatio = (
    canvas: fabric.Canvas,
    instance: PanZoom,
): void => {
    const zoomRatioW = (innerWidth - 40) / canvas.width!;
    const zoomRatioH = (innerHeight - 100) / canvas.height!;
    const zoomRatio = zoomRatioW < zoomRatioH ? zoomRatioW : zoomRatioH;
    zoomResetValue.set(zoomRatio);
    instance.zoomAbs(0, 0, zoomRatio);
};

const addClozeNotesToTextEditor = (header: string, backExtra: string, tags: string[]) => {
    const noteFieldsData: { id: string; title: string; divValue: string; textareaValue: string }[] = get(
        notesDataStore,
    );
    noteFieldsData[0].divValue = header;
    noteFieldsData[1].divValue = backExtra;
    noteFieldsData[0].textareaValue = header;
    noteFieldsData[1].textareaValue = backExtra;
    tagsWritable.set(tags);

    noteFieldsData.forEach((note) => {
        const divId = `${note.id}--div`;
        const textAreaId = `${note.id}--textarea`;
        const divElement = document.getElementById(divId)!;
        const textAreaElement = document.getElementById(textAreaId)! as HTMLTextAreaElement;
        divElement.innerHTML = note.divValue;
        textAreaElement.value = note.textareaValue;
    });
};

function containerSize(): Size {
    const container = document.querySelector(".editor-main")!;
    return {
        width: container.clientWidth,
        height: container.clientHeight,
    };
}

export async function resetIOImage(path) {
    const imageData = await getImageForOcclusion({ path });
    const image = document.getElementById("image") as HTMLImageElement;
    image.src = getImageData(imageData.data!);
    const canvas = globalThis.canvas;

    image.onload = function() {
        const size = optimumCssSizeForCanvas(
            { width: image.naturalWidth, height: image.naturalHeight },
            containerSize(),
        );
        canvas.setWidth(size.width);
        canvas.setHeight(size.height);
        image.height = size.height;
        image.width = size.width;
    };
}
globalThis.resetIOImage = resetIOImage;
