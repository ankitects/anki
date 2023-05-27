// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";
import type { ImageOcclusion } from "@tslib/proto";
import { fabric } from "fabric";
import type { PanZoom } from "panzoom";
import protobuf from "protobufjs";
import { get } from "svelte/store";

import { cappedCanvasSize } from "./canvas-cap";
import { getImageForOcclusion, getImageOcclusionNote } from "./lib";
import { notesDataStore, tagsWritable, zoomResetValue } from "./store";
import Toast from "./Toast.svelte";
import { addShapesToCanvasFromCloze } from "./tools/add-from-cloze";
import { enableSelectable, moveShapeToCanvasBoundaries } from "./tools/lib";
import { undoRedoInit } from "./tools/tool-undo-redo";

export const setupMaskEditor = async (path: string, instance: PanZoom): Promise<fabric.Canvas> => {
    const imageData = await getImageForOcclusion(path!);
    const canvas = initCanvas();

    // get image width and height
    const image = document.getElementById("image") as HTMLImageElement;
    image.src = getImageData(imageData.data!);
    image.onload = function() {
        const size = cappedCanvasSize({ width: image.width, height: image.height });
        canvas.setWidth(size.width);
        canvas.setHeight(size.height);
        image.height = size.height;
        image.width = size.width;
        setCanvasZoomRatio(canvas, instance);
    };

    return canvas;
};

export const setupMaskEditorForEdit = async (noteId: number, instance: PanZoom): Promise<fabric.Canvas> => {
    const clozeNoteResponse: ImageOcclusion.GetImageOcclusionNoteResponse = await getImageOcclusionNote(noteId);
    if (clozeNoteResponse.error) {
        new Toast({
            target: document.body,
            props: {
                message: tr.notetypesErrorGettingImagecloze(),
                type: "error",
            },
        }).$set({ showToast: true });
        return;
    }

    const clozeNote = clozeNoteResponse.note!;
    const canvas = initCanvas();

    // get image width and height
    const image = document.getElementById("image") as HTMLImageElement;
    image.src = getImageData(clozeNote.imageData!);
    image.onload = function() {
        const size = cappedCanvasSize({ width: image.width, height: image.height });
        canvas.setWidth(size.width);
        canvas.setHeight(size.height);
        image.height = size.height;
        image.width = size.width;

        setCanvasZoomRatio(canvas, instance);
        addShapesToCanvasFromCloze(canvas, clozeNote.occlusions);
        enableSelectable(canvas, true);
        addClozeNotesToTextEditor(clozeNote.header, clozeNote.backExtra, clozeNote.tags);
    };

    return canvas;
};

const initCanvas = (): fabric.Canvas => {
    const canvas = new fabric.Canvas("canvas");
    tagsWritable.set([]);
    globalThis.canvas = canvas;
    // enables uniform scaling by default without the need for the Shift key
    canvas.uniformScaling = false;
    canvas.uniScaleKey = "none";
    moveShapeToCanvasBoundaries(canvas);
    undoRedoInit(canvas);
    return canvas;
};

const getImageData = (imageData): string => {
    const b64encoded = protobuf.util.base64.encode(
        imageData,
        0,
        imageData.length,
    );
    return "data:image/png;base64," + b64encoded;
};

const setCanvasZoomRatio = (
    canvas: fabric.Canvas,
    instance: PanZoom,
): void => {
    const zoomRatioW = (innerWidth - 40) / canvas.width!;
    const zoomRatioH = (innerHeight - 100) / canvas.height!;
    const zoomRatio = zoomRatioW < zoomRatioH ? zoomRatioW : zoomRatioH;
    zoomResetValue.set(zoomRatio);
    instance.smoothZoom(0, 0, zoomRatio);
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
