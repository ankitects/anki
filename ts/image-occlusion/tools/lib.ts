// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type fabric from "fabric";
import type { PanZoom } from "panzoom";
import { get } from "svelte/store";

import { zoomResetValue } from "../store";
import { stack } from "./tool-undo-redo";

let _clipboard;

export const stopDraw = (canvas: fabric.Canvas): void => {
    canvas.off("mouse:down");
    canvas.off("mouse:up");
    canvas.off("mouse:move");
};

export const enableSelectable = (canvas: fabric.Canvas, select: boolean): void => {
    canvas.selection = select;
    canvas.forEachObject(function(o) {
        o.selectable = select;
    });
    canvas.renderAll();
};

export const deleteItem = (canvas: fabric.Canvas): void => {
    const active = canvas.getActiveObject();
    if (active) {
        canvas.remove(active);
        stack.push(active);
        if (active.type == "activeSelection") {
            active.getObjects().forEach((x) => canvas.remove(x));
            canvas.discardActiveObject().renderAll();
        }
    }
};

export const duplicateItem = (canvas: fabric.Canvas): void => {
    if (!canvas.getActiveObject()) {
        return;
    }
    copyItem(canvas);
    pasteItem(canvas);
};

export const groupShapes = (canvas: fabric.Canvas): void => {
    if (
        !canvas.getActiveObject()
        || canvas.getActiveObject().type !== "activeSelection"
    ) {
        return;
    }

    canvas.getActiveObject().toGroup();
    canvas.requestRenderAll();
};

export const unGroupShapes = (canvas: fabric.Canvas): void => {
    if (
        !canvas.getActiveObject()
        || canvas.getActiveObject().type !== "group"
    ) {
        return;
    }

    const group = canvas.getActiveObject();
    const items = group.getObjects();
    group._restoreObjectsState();
    canvas.remove(group);

    items.forEach((item) => {
        canvas.add(item);
    });

    canvas.requestRenderAll();
};

export const getQuestionMaskColor = (): string => {
    return localStorage.getItem("ques-color")
        ? localStorage.getItem("ques-color")!
        : "#f06";
};

export const getShapeColor = (): string => {
    return localStorage.getItem("shape-color")
        ? localStorage.getItem("shape-color")!
        : "#f06";
};

export const zoomIn = (instance: PanZoom): void => {
    instance.smoothZoom(0, 0, 1.25);
};

export const zoomOut = (instance: PanZoom): void => {
    instance.smoothZoom(0, 0, 0.5);
};

export const zoomReset = (instance: PanZoom): void => {
    instance.moveTo(0, 0);
    instance.smoothZoomAbs(0, 0, get(zoomResetValue));
};

const copyItem = (canvas: fabric.Canvas): void => {
    if (!canvas.getActiveObject()) {
        return;
    }

    // clone what are you copying since you
    // may want copy and paste on different moment.
    // and you do not want the changes happened
    // later to reflect on the copy.
    canvas.getActiveObject().clone(function(cloned) {
        _clipboard = cloned;
    });
};

const pasteItem = (canvas: fabric.Canvas): void => {
    // clone again, so you can do multiple copies.
    _clipboard.clone(function(clonedObj) {
        canvas.discardActiveObject();

        clonedObj.set({
            left: clonedObj.left + 10,
            top: clonedObj.top + 10,
            evented: true,
        });

        if (clonedObj.type === "activeSelection") {
            // active selection needs a reference to the canvas.
            clonedObj.canvas = canvas;
            clonedObj.forEachObject(function(obj) {
                canvas.add(obj);
            });

            // this should solve the unselectability
            clonedObj.setCoords();
        } else {
            canvas.add(clonedObj);
        }

        _clipboard.top += 10;
        _clipboard.left += 10;
        canvas.setActiveObject(clonedObj);
        canvas.requestRenderAll();
    });
};

export const fillShapeColor = (canvas: fabric.Canvas): void => {
    const color = getShapeColor();
    const active = canvas.getActiveObject();
    if (active) {
        if (active.type === "activeSelection") {
            active.getObjects().forEach((x) => x.set({ fill: color }));
            canvas.renderAll();
        } else {
            active.set({ fill: color });
            canvas.renderAll();
        }
    }
};

export const fillQuestionMaskColor = (canvas: fabric.Canvas): void => {
    const color = getQuestionMaskColor();
    const active = canvas.getActiveObject();
    if (active) {
        if (active.type === "activeSelection") {
            active.getObjects().forEach((x) => x.set({ questionmaskcolor: color }));
            canvas.renderAll();
        } else {
            active.set({ questionmaskcolor: color });
            canvas.renderAll();
        }
    }
};

export const makeMaskTransparent = (canvas: fabric.Canvas, opacity = false): void => {
    const objects = canvas.getObjects();
    objects.forEach((object) => {
        object.set({
            opacity: opacity ? 0.4 : 1,
            transparentCorners: false,
        });
    });
    canvas.renderAll();
};
