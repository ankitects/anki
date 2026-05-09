// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";
import { get } from "svelte/store";

import { opacityStateStore } from "../store";
import { BORDER_COLOR, isPointerInBoundingBox, SHAPE_MASK_COLOR, stopDraw } from "./lib";
import { undoStack } from "./tool-undo-redo";
import { onPinchZoom } from "./tool-zoom";

export const drawRectangle = (canvas: fabric.Canvas): void => {
    canvas.selectionColor = "rgba(0, 0, 0, 0)";
    let rect, isDown, origX, origY;

    stopDraw(canvas);

    canvas.on("mouse:down", function(o) {
        if (o.target) {
            return;
        }
        isDown = true;

        const pointer = canvas.getPointer(o.e);
        origX = pointer.x;
        origY = pointer.y;

        if (!isPointerInBoundingBox(pointer)) {
            isDown = false;
            return;
        }

        rect = new fabric.Rect({
            left: origX,
            top: origY,
            originX: "left",
            originY: "top",
            width: pointer.x - origX,
            height: pointer.y - origY,
            angle: 0,
            fill: SHAPE_MASK_COLOR,
            transparentCorners: false,
            selectable: true,
            stroke: BORDER_COLOR,
            strokeWidth: 1,
            strokeUniform: true,
            noScaleCache: false,
            opacity: get(opacityStateStore) ? 0.4 : 1,
        });
        rect["id"] = "rect-" + new Date().getTime();

        canvas.add(rect);
    });

    canvas.on("mouse:move", function(o) {
        if (onPinchZoom(o)) {
            canvas.remove(rect);
            canvas.renderAll();
            return;
        }

        if (!isDown) {
            return;
        }
        const pointer = canvas.getPointer(o.e);
        const x = pointer.x;
        const y = pointer.y;

        if (x < origX) {
            rect.set({ originX: "right" });
        } else {
            rect.set({ originX: "left" });
        }

        if (y < origY) {
            rect.set({ originY: "bottom" });
        } else {
            rect.set({ originY: "top" });
        }

        rect.set({
            width: Math.abs(x - rect.left),
            height: Math.abs(y - rect.top),
        });

        canvas.renderAll();
    });

    canvas.on("mouse:up", function() {
        isDown = false;
        // probably changed from rectangle to ellipse
        if (!rect) {
            return;
        }
        if (rect.width < 5 || rect.height < 5) {
            canvas.remove(rect);
            rect = undefined;
            return;
        }

        if (rect.originX === "right") {
            rect.set({
                originX: "left",
                left: rect.left - rect.width + rect.strokeWidth,
            });
        }

        if (rect.originY === "bottom") {
            rect.set({
                originY: "top",
                top: rect.top - rect.height + rect.strokeWidth,
            });
        }

        rect.setCoords();
        canvas.setActiveObject(rect);
        undoStack.onObjectAdded(rect.id);
        rect = undefined;
    });
};
