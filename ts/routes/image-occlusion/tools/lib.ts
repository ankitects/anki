// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";
import { get } from "svelte/store";

import { opacityStateStore, saveNeededStore } from "../store";
import type { Size } from "../types";

export const SHAPE_MASK_COLOR = "#ffeba2";
export const BORDER_COLOR = "#212121";
export const TEXT_BACKGROUND_COLOR = "#ffffff";
export const TEXT_FONT_FAMILY = "Arial";
export const TEXT_PADDING = 5;
export const TEXT_FONT_SIZE = 40;
export const TEXT_COLOR = "#000000";

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
            (active as fabric.ActiveSelection).getObjects().forEach((x) => canvas.remove(x));
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
        canvas.getActiveObject()?.type !== "activeSelection"
    ) {
        return;
    }

    const activeObject = canvas.getActiveObject() as fabric.ActiveSelection;
    const items = activeObject.getObjects();

    let minOrdinal: number | undefined = Math.min(...items.map((item) => item.ordinal));
    minOrdinal = Number.isNaN(minOrdinal) ? undefined : minOrdinal;

    items.forEach((item) => {
        item.set({ opacity: 1, ordinal: minOrdinal });
    });

    activeObject.toGroup().set({
        opacity: get(opacityStateStore) ? 0.4 : 1,
    }).setControlsVisibility({ mtr: false });

    redraw(canvas);
};

export const unGroupShapes = (canvas: fabric.Canvas): void => {
    if (
        canvas.getActiveObject()?.type !== "group"
    ) {
        return;
    }

    const group = canvas.getActiveObject() as fabric.Group;
    const items = group.getObjects();
    group._restoreObjectsState();
    group.destroyed = true;

    items.forEach((item) => {
        item.set({
            opacity: get(opacityStateStore) ? 0.4 : 1,
            ordinal: undefined,
        });
        canvas.add(item);
    });

    canvas.remove(group);
    redraw(canvas);
};

/** Check for the target within a (potentially nested) group
 * NOTE: assumes that masks do not overlap */
export const findTargetInGroup = (group: fabric.Group, p: fabric.Point): fabric.Object | undefined => {
    if (!group) { return; }
    const point = fabric.util.transformPoint(p, fabric.util.invertTransform(group.calcOwnMatrix()));
    for (const shape of group.getObjects()) {
        if (shape instanceof fabric.Group) {
            const ret = findTargetInGroup(shape, point);
            if (ret) { return ret; }
        } else if (shape.containsPoint(point)) {
            return shape;
        }
    }
};

const copyItem = (canvas: fabric.Canvas): void => {
    const activeObject = canvas.getActiveObject();
    if (!activeObject) {
        return;
    }

    // clone what are you copying since you
    // may want copy and paste on different moment.
    // and you do not want the changes happened
    // later to reflect on the copy.
    activeObject.clone(function(cloned) {
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
            modifiedEllipse(boundingBox, activeObject as unknown as fabric.Ellipse);
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
    const newWidth = object.width! * object.scaleX!;
    const newHeight = object.height! * object.scaleY!;

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
    object: fabric.Ellipse,
): void => {
    const newRx = object.rx! * object.scaleX!;
    const newRy = object.ry! * object.scaleY!;
    const newWidth = object.width! * object.scaleX!;
    const newHeight = object.height! * object.scaleY!;

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
    const { left, top, width, height } = object.getBoundingRect(true);

    if (left < 0) {
        object.set({ left: Math.max(object.left! - left, 0) });
    }
    if (top < 0) {
        object.set({ top: Math.max(object.top! - top, 0) });
    }
    if (left > boundingBox.width!) {
        object.set({ left: object.left! - left - width + boundingBox.width! });
    }
    if (top > boundingBox.height!) {
        object.set({ top: object.top! - top - height + boundingBox.height! });
    }

    object.setCoords();
    saveNeededStore.set(true);
};

export function enableUniformScaling(canvas: fabric.Canvas, obj: fabric.Object): void {
    obj.setControlsVisibility({ mb: false, ml: false, mt: false, mr: false });
    let timer: number;
    obj.on("scaling", (e) => {
        if (["bl", "br", "tr", "tl"].includes(e.transform!.corner)) {
            clearTimeout(timer);
            canvas.uniformScaling = true;
            // https://github.com/sveltejs/kit/issues/9348
            timer = setTimeout(() => {
                canvas.uniformScaling = false;
            }, 500) as unknown as number;
        }
    });
}

export function addBorder(obj: fabric.Object): void {
    obj.stroke = BORDER_COLOR;
    obj.strokeWidth = 1;
    obj.strokeUniform = true;
}

export const redraw = (canvas: fabric.Canvas): void => {
    canvas.requestRenderAll();
};

export const clear = (canvas: fabric.Canvas): void => {
    canvas.clear();
};

/**
 * Creates a canvas event listener on shape movement to restrict movement to within the `boundingBox`
 */
export const makeShapesRemainInCanvas = (canvas: fabric.Canvas, boundingBox: fabric.Rect) => {
    canvas.on("object:moving", function(e) {
        const obj = e.target!;

        const { left: objBbLeft, top: objBbTop, width: objBbWidth, height: objBbHeight } = obj.getBoundingRect(
            true,
            true,
        );

        if (objBbWidth > boundingBox.width! || objBbHeight > boundingBox.height!) {
            return;
        }

        const topBound = boundingBox.top!;
        const bottomBound = topBound + boundingBox.height! + 5;
        const leftBound = boundingBox.left!;
        const rightBound = leftBound + boundingBox.width! + 5;

        const newBbLeft = Math.min(Math.max(objBbLeft, leftBound), rightBound - objBbWidth);
        const newBbTop = Math.min(Math.max(objBbTop, topBound), bottomBound - objBbHeight);

        obj.left = obj.left! + newBbLeft - objBbLeft;
        obj.top = obj.top! + newBbTop - objBbTop;
    });
};

export const selectAllShapes = (canvas: fabric.Canvas) => {
    canvas.discardActiveObject();
    // filter out the transparent bounding box from the selection
    const sel = new fabric.ActiveSelection(
        canvas.getObjects().filter((obj) => obj.fill !== "transparent"),
        {
            canvas: canvas,
        },
    );
    canvas.setActiveObject(sel);
    redraw(canvas);
};

export const isPointerInBoundingBox = (pointer): boolean => {
    const boundingBox = getBoundingBox();
    if (boundingBox === undefined) {
        return false;
    }
    boundingBox.selectable = false;
    boundingBox.evented = false;
    if (
        pointer.x < boundingBox.left!
        || pointer.x > boundingBox.left! + boundingBox.width!
        || pointer.y < boundingBox.top!
        || pointer.y > boundingBox.top! + boundingBox.height!
    ) {
        return false;
    }
    return true;
};

export const getBoundingBox = (): fabric.Rect | undefined => {
    const canvas: fabric.Canvas = globalThis.canvas;
    return canvas.getObjects().find((obj) => obj.fill === "transparent");
};

export const getBoundingBoxSize = (): Size => {
    const boundingBoxSize = getBoundingBox()?.getBoundingRect(true);
    if (boundingBoxSize) {
        return { width: boundingBoxSize.width, height: boundingBoxSize.height };
    }
    return { width: 0, height: 0 };
};
