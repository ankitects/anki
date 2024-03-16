// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";
import { get } from "svelte/store";

import { opacityStateStore } from "../store";

export const SHAPE_MASK_COLOR = "#ffeba2";
export const BORDER_COLOR = "#212121";
export const TEXT_BACKGROUND_COLOR = "#ffffff";
export const TEXT_FONT_FAMILY = "Arial";
export const TEXT_PADDING = 5;

let _clipboard;

export const stopDraw = (canvas: fabric.Canvas): void => {
    canvas.off("mouse:down");
    canvas.off("mouse:up");
    canvas.off("mouse:move");
};

export const enableSelectable = (
    canvas: fabric.Canvas,
    select: boolean,
): void => {
    canvas.selection = select;
    canvas.forEachObject(function(o) {
        if (o.fill === "transparent") {
            return;
        }
        o.selectable = select;
    });
    canvas.renderAll();
};

export const deleteItem = (canvas: fabric.Canvas): void => {
    const active = canvas.getActiveObject();
    if (active) {
        canvas.remove(active);
        if (active.type == "activeSelection") {
            active.getObjects().forEach((x) => canvas.remove(x));
            canvas.discardActiveObject().renderAll();
        }
    }
    redraw(canvas);
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

    const activeObject = canvas.getActiveObject();
    const items = activeObject.getObjects();
    items.forEach((item) => {
        item.set({ opacity: 1 });
    });
    activeObject.toGroup().set({
        opacity: get(opacityStateStore) ? 0.4 : 1,
    });
    redraw(canvas);
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
    group.destroyed = true;
    canvas.remove(group);

    items.forEach((item) => {
        item.set({ opacity: get(opacityStateStore) ? 0.4 : 1 });
        canvas.add(item);
    });

    redraw(canvas);
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
        redraw(canvas);
    });
};

export const makeMaskTransparent = (
    canvas: fabric.Canvas,
    opacity = false,
): void => {
    opacityStateStore.set(opacity);
    const objects = canvas.getObjects();
    objects.forEach((object) => {
        object.set({
            opacity: opacity ? 0.4 : 1,
            transparentCorners: false,
        });
    });
    canvas.renderAll();
};

export const moveShapeToCanvasBoundaries = (canvas: fabric.Canvas, boundingBox: fabric.Rect): void => {
    canvas.on("object:modified", function(o) {
        const activeObject = o.target;
        if (!activeObject) {
            return;
        }
        if (activeObject.type === "rect") {
            modifiedRectangle(boundingBox, activeObject);
        }
        if (activeObject.type === "ellipse") {
            modifiedEllipse(boundingBox, activeObject);
        }
        if (activeObject.type === "i-text") {
            modifiedText(boundingBox, activeObject);
        }
    });
};

const modifiedRectangle = (
    boundingBox: fabric.Rect,
    object: fabric.Object,
): void => {
    const newWidth = object.width * object.scaleX;
    const newHeight = object.height * object.scaleY;

    object.set({
        width: newWidth,
        height: newHeight,
        scaleX: 1,
        scaleY: 1,
    });
    setShapePosition(boundingBox, object);
};

const modifiedEllipse = (
    boundingBox: fabric.Rect,
    object: fabric.Object,
): void => {
    const newRx = object.rx * object.scaleX;
    const newRy = object.ry * object.scaleY;
    const newWidth = object.width * object.scaleX;
    const newHeight = object.height * object.scaleY;

    object.set({
        rx: newRx,
        ry: newRy,
        width: newWidth,
        height: newHeight,
        scaleX: 1,
        scaleY: 1,
    });
    setShapePosition(boundingBox, object);
};

const modifiedText = (boundingBox: fabric.Rect, object: fabric.Object): void => {
    setShapePosition(boundingBox, object);
};

const setShapePosition = (
    boundingBox: fabric.Rect,
    object: fabric.Object,
): void => {
    if (object.left < 0) {
        object.set({ left: 0 });
    }
    if (object.top < 0) {
        object.set({ top: 0 });
    }
    if (object.left + object.width * object.scaleX + object.strokeWidth > boundingBox.width) {
        object.set({ left: boundingBox.width - object.width * object.scaleX });
    }
    if (object.top + object.height * object.scaleY + object.strokeWidth > boundingBox.height) {
        object.set({ top: boundingBox.height - object.height * object.scaleY });
    }
    object.setCoords();
};

export function enableUniformScaling(canvas: fabric.Canvas, obj: fabric.Object): void {
    obj.setControlsVisibility({ mb: false, ml: false, mt: false, mr: false });
    let timer: number;
    obj.on("scaling", (e) => {
        if (["bl", "br", "tr", "tl"].includes(e.transform.corner)) {
            clearTimeout(timer);
            canvas.uniformScaling = true;
            timer = setTimeout(() => {
                canvas.uniformScaling = false;
            }, 500);
        }
    });
}

export function addBorder(obj: fabric.Object): void {
    obj.stroke = BORDER_COLOR;
}

export const redraw = (canvas: fabric.Canvas): void => {
    canvas.requestRenderAll();
};

export const clear = (canvas: fabric.Canvas): void => {
    canvas.clear();
};

export const makeShapeRemainInCanvas = (canvas: fabric.Canvas, boundingBox: fabric.Rect) => {
    canvas.on("object:moving", function(e) {
        const obj = e.target;
        if (obj.getScaledHeight() > boundingBox.height || obj.getScaledWidth() > boundingBox.width) {
            return;
        }

        obj.setCoords();

        const top = obj.top;
        const left = obj.left;

        const topBound = boundingBox.top;
        const bottomBound = topBound + boundingBox.height;
        const leftBound = boundingBox.left;
        const rightBound = leftBound + boundingBox.width;

        obj.left = Math.min(Math.max(left, leftBound), rightBound - obj.width);
        obj.top = Math.min(Math.max(top, topBound), bottomBound - obj.height);
    });
};

export const selectAllShapes = (canvas: fabric.Canvas) => {
    canvas.discardActiveObject();
    const sel = new fabric.ActiveSelection(canvas.getObjects(), {
        canvas: canvas,
    });
    canvas.setActiveObject(sel);
    redraw(canvas);
};

export const isPointerInBoundingBox = (pointer): boolean => {
    const boundingBox = getBoundingBox();
    boundingBox.selectable = false;
    boundingBox.evented = false;
    if (
        pointer.x < boundingBox.left
        || pointer.x > boundingBox.left + boundingBox.width
        || pointer.y < boundingBox.top
        || pointer.y > boundingBox.top + boundingBox.height
    ) {
        return false;
    }
    return true;
};

export const getBoundingBox = () => {
    const canvas = globalThis.canvas;
    return canvas.getObjects().find((obj) => obj.fill === "transparent");
};
