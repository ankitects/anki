// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";
import type { ImageOcclusion } from "@tslib/proto";
import { fabric } from "fabric";
import type { PanZoom } from "panzoom";
import protobuf from "protobufjs";
import { get } from "svelte/store";

import { getImageClozeNote, getImageForOcclusion } from "./lib";
import { notesDataStore, tagsWritable, zoomResetValue } from "./store";
import Toast from "./Toast.svelte";
import { enableSelectable } from "./tools/lib";
import { generateShapeFromCloze } from "./tools/shape-generate";
import { undoRedoInit } from "./tools/tool-undo-redo";

export const setupMaskEditor = async (path: string, instance: PanZoom): Promise<fabric.Canvas> => {
    const metadata = await getImageForOcclusion(path!);
    const b64encoded = protobuf.util.base64.encode(
        metadata.data,
        0,
        metadata.data.length,
    );
    const data = "data:image/png;base64," + b64encoded;

    const canvas = new fabric.Canvas("canvas", {
        hoverCursor: "pointer",
        selectionBorderColor: "green",
    });

    // get image width and height
    const image = new Image();
    image.onload = function() {
        const size = limitSize({ width: image.width, height: image.height });
        canvas.setWidth(size.width);
        canvas.setHeight(size.height);

        fabric.Image.fromURL(image.src, function(image) {
            canvas.setBackgroundImage(image, canvas.renderAll.bind(canvas), {
                scaleX: canvas.width! / image.width!,
                scaleY: canvas.height! / image.height!,
            });

            canvas.backgroundImage.scale(size.scalar);
            const zoomRatio = (innerWidth - 40) / canvas.width!;
            zoomResetValue.set(zoomRatio);
            instance.smoothZoom(0, 0, zoomRatio);
        });
    };

    tagsWritable.set([]);
    globalThis.canvas = canvas;
    undoRedoInit(canvas);
    image.src = data;
    image.remove();
    return canvas;
};

export const setupMaskEditorForEdit = async (noteId: number, instance: PanZoom): Promise<fabric.Canvas> => {
    const clozeNoteResponse: ImageOcclusion.ImageClozeNoteResponse = await getImageClozeNote(noteId);
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
    const b64encoded = protobuf.util.base64.encode(
        clozeNote.imageData,
        0,
        clozeNote.imageData.length,
    );
    const data = "data:image/png;base64," + b64encoded;

    const canvas = new fabric.Canvas("canvas", {
        hoverCursor: "pointer",
        selectionBorderColor: "green",
    });

    // get image width and height
    const image = new Image();
    image.onload = function() {
        const size = limitSize({ width: image.width, height: image.height });
        canvas.setWidth(size.width);
        canvas.setHeight(size.height);

        fabric.Image.fromURL(image.src, function(image) {
            canvas.setBackgroundImage(image, canvas.renderAll.bind(canvas), {
                scaleX: canvas.width / image.width,
                scaleY: canvas.height / image.height,
            });

            canvas.backgroundImage.scale(size.scalar);
            generateShapeFromCloze(canvas, clozeNote.occlusions);
            enableSelectable(canvas, true);
            addClozeNotesToTextEditor(clozeNote.header, clozeNote.backExtra, clozeNote.tags);

            const zoomRatio = (innerWidth - 40) / canvas.width!;
            zoomResetValue.set(zoomRatio);
            instance.smoothZoom(0, 0, zoomRatio);
        });
    };

    globalThis.canvas = canvas;
    undoRedoInit(canvas);
    image.src = data;
    image.remove();
    return canvas;
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

const limitSize = (size: { width: number; height: number }): { width: number; height: number; scalar: number } => {
    const maximumPixels = 1000000;
    const { width, height } = size;

    const requiredPixels = width * height;
    if (requiredPixels <= maximumPixels) return { width, height, scalar: 1 };

    const scalar = Math.sqrt(maximumPixels) / Math.sqrt(requiredPixels);
    return {
        width: Math.floor(width * scalar),
        height: Math.floor(height * scalar),
        scalar: scalar,
    };
};
