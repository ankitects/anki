// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { protoBase64 } from "@bufbuild/protobuf";
import { getImageForOcclusion, getImageOcclusionNote } from "@generated/backend";
import * as tr from "@generated/ftl";
import { fabric } from "fabric";
import { get } from "svelte/store";

import { optimumCssSizeForCanvas } from "./canvas-scale";
import { hideAllGuessOne, notesDataStore, saveNeededStore, tagsWritable, textEditingState } from "./store";
import Toast from "./Toast.svelte";
import { addShapesToCanvasFromCloze } from "./tools/add-from-cloze";
import { enableSelectable, makeShapesRemainInCanvas, moveShapeToCanvasBoundaries } from "./tools/lib";
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
    onImageLoaded: (event: ImageLoadedEvent) => void,
): Promise<fabric.Canvas> => {
    const imageData = await getImageForOcclusion({ path });
    const canvas = initCanvas();

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
        throw "error getting cloze";
    }

    const clozeNote = clozeNoteResponse.value.value;
    const canvas = initCanvas();

    hideAllGuessOne.set(clozeNote.occludeInactive);

    // get image width and height
    const image = document.getElementById("image") as HTMLImageElement;
    image.src = getImageData(clozeNote.imageData!, clozeNote.imageFileName!);

    image.onload = async function() {
        const size = optimumCssSizeForCanvas(
            { width: image.naturalWidth, height: image.naturalHeight },
            containerSize(),
        );
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

function initCanvas(): fabric.Canvas {
    const canvas = new fabric.Canvas("canvas");
    tagsWritable.set([]);
    globalThis.canvas = canvas;
    undoStack.setCanvas(canvas);
    // find object per-pixel basis rather than according to bounding box,
    // allow click through transparent area
    fabric.Object.prototype.perPixelTargetFind = true;
    // Disable uniform scaling
    canvas.uniformScaling = false;
    canvas.uniScaleKey = "none";
    // disable object caching
    fabric.Object.prototype.objectCaching = false;
    // add a border to corner to handle blend of control
    fabric.Object.prototype.transparentCorners = false;
    fabric.Object.prototype.cornerStyle = "circle";
    fabric.Object.prototype.cornerStrokeColor = "#000000";
    fabric.Object.prototype.padding = 8;
    // disable rotation when selecting
    canvas.on("selection:created", () => {
        const g = canvas.getActiveObject();
        if (g && g instanceof fabric.Group) { g.setControlsVisibility({ mtr: false }); }
    });
    canvas.on("object:modified", (evt) => {
        if (evt.target instanceof fabric.Polygon) {
            modifiedPolygon(canvas, evt.target);
            undoStack.onObjectModified();
        }
    });
    canvas.on("text:editing:entered", function() {
        textEditingState.set(true);
    });

    canvas.on("text:editing:exited", function() {
        textEditingState.set(false);
    });
    canvas.on("object:removed", () => {
        saveNeededStore.set(true);
    });
    return canvas;
}

const setupBoundingBox = (canvas: fabric.Canvas, size: Size): fabric.Rect => {
    const boundingBox = new fabric.Rect({
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
    boundingBox["id"] = "boundingBox";

    canvas.add(boundingBox);
    onResize(canvas);
    makeShapesRemainInCanvas(canvas, boundingBox);
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
