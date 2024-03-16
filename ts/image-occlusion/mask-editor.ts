// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { protoBase64 } from "@bufbuild/protobuf";
import { getImageForOcclusion, getImageOcclusionNote } from "@tslib/backend";
import * as tr from "@tslib/ftl";
import { fabric } from "fabric";
import { get } from "svelte/store";

import { optimumCssSizeForCanvas } from "./canvas-scale";
import { notesDataStore, tagsWritable } from "./store";
import Toast from "./Toast.svelte";
import { addShapesToCanvasFromCloze } from "./tools/add-from-cloze";
import { enableSelectable, makeShapeRemainInCanvas, moveShapeToCanvasBoundaries } from "./tools/lib";
import { modifiedPolygon } from "./tools/tool-polygon";
import { undoStack } from "./tools/tool-undo-redo";
import { enablePinchZoom, onResize, setCanvasSize } from "./tools/tool-zoom";
import type { Size } from "./types";

export interface ImageLoadedEvent {
    path?: string;
    noteId?: bigint;
}

export const setupMaskEditor = async (
    path: string,
    onChange: () => void,
    onImageLoaded: (event: ImageLoadedEvent) => void,
): Promise<fabric.Canvas> => {
    const imageData = await getImageForOcclusion({ path });
    const canvas = initCanvas(onChange);

    // get image width and height
    const image = document.getElementById("image") as HTMLImageElement;
    image.src = getImageData(imageData.data!, path);
    image.onload = function() {
        const size = optimumCssSizeForCanvas({ width: image.width, height: image.height }, containerSize());
        setCanvasSize(canvas);
        onImageLoaded({ path });
        setupBoundingBox(canvas, size);
        undoStack.reset();
    };

    return canvas;
};

export const setupMaskEditorForEdit = async (
    noteId: number,
    onChange: () => void,
    onImageLoaded: (event: ImageLoadedEvent) => void,
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
    image.src = getImageData(clozeNote.imageData!, clozeNote.imageFileName!);

    image.onload = async function() {
        const size = optimumCssSizeForCanvas({ width: image.width, height: image.height }, containerSize());
        setCanvasSize(canvas);
        const boundingBox = setupBoundingBox(canvas, size);
        addShapesToCanvasFromCloze(canvas, boundingBox, clozeNote.occlusions);
        enableSelectable(canvas, true);
        addClozeNotesToTextEditor(clozeNote.header, clozeNote.backExtra, clozeNote.tags);
        undoStack.reset();
        window.requestAnimationFrame(() => {
            onImageLoaded({ noteId: BigInt(noteId) });
        });
    };

    return canvas;
};

function initCanvas(onChange: () => void): fabric.Canvas {
    const canvas = new fabric.Canvas("canvas");
    tagsWritable.set([]);
    globalThis.canvas = canvas;
    undoStack.setCanvas(canvas);
    // find object per-pixel basis rather than according to bounding box,
    // allow click through transparent area
    canvas.perPixelTargetFind = true;
    // Disable uniform scaling
    canvas.uniformScaling = false;
    canvas.uniScaleKey = "none";
    // disable rotation globally
    delete fabric.Object.prototype.controls.mtr;
    // disable object caching
    fabric.Object.prototype.objectCaching = false;
    // add a border to corner to handle blend of control
    fabric.Object.prototype.transparentCorners = false;
    fabric.Object.prototype.cornerStyle = "circle";
    fabric.Object.prototype.cornerStrokeColor = "#000000";
    fabric.Object.prototype.padding = 8;
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

const setupBoundingBox = (canvas: fabric.Canvas, size: Size): fabric.Rect => {
    const boundingBox = new fabric.Rect({
        id: "boundingBox",
        fill: "transparent",
        width: size.width,
        height: size.height,
        hasBorders: false,
        hasControls: false,
        lockMovementX: true,
        lockMovementY: true,
        selectable: false,
        evented: false,
    });

    canvas.add(boundingBox);
    onResize(canvas);
    makeShapeRemainInCanvas(canvas, boundingBox);
    moveShapeToCanvasBoundaries(canvas, boundingBox);
    // enable pinch zoom for mobile devices
    enablePinchZoom(canvas);
    return boundingBox;
};

const getImageData = (imageData, path): string => {
    const b64encoded = protoBase64.enc(imageData);
    const extension = path.split(".").pop();
    const mimeTypes = {
        "jpg": "jpeg",
        "jpeg": "jpeg",
        "gif": "gif",
        "svg": "svg+xml",
        "webp": "webp",
        "avif": "avif",
        "png": "png",
    };

    const type = mimeTypes[extension] || "png";
    return `data:image/${type};base64,${b64encoded}`;
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

export async function resetIOImage(path: string, onImageLoaded: (event: ImageLoadedEvent) => void) {
    const imageData = await getImageForOcclusion({ path });
    const image = document.getElementById("image") as HTMLImageElement;
    image.src = getImageData(imageData.data!, path);
    const canvas = globalThis.canvas;

    image.onload = async function() {
        const size = optimumCssSizeForCanvas(
            { width: image.naturalWidth, height: image.naturalHeight },
            containerSize(),
        );
        image.width = size.width;
        image.height = size.height;
        setCanvasSize(canvas);
        onImageLoaded({ path });
        setupBoundingBox(canvas, size);
    };
}
globalThis.resetIOImage = resetIOImage;
