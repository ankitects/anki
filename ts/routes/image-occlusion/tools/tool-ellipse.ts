// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";
import { get } from "svelte/store";

import { opacityStateStore } from "../store";
import { BORDER_COLOR, isPointerInBoundingBox, SHAPE_MASK_COLOR, stopDraw } from "./lib";
import { undoStack } from "./tool-undo-redo";
import { onPinchZoom } from "./tool-zoom";

export const drawEllipse = (canvas: fabric.Canvas): void => {
    canvas.selectionColor = "rgba(0, 0, 0, 0)";
    let ellipse, isDown, origX, origY;

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

        ellipse = new fabric.Ellipse({
            left: origX,
            top: origY,
            originX: "left",
            originY: "top",
            rx: pointer.x - origX,
            ry: pointer.y - origY,
            fill: SHAPE_MASK_COLOR,
            transparentCorners: false,
            selectable: true,
            stroke: BORDER_COLOR,
            strokeWidth: 1,
            strokeUniform: true,
            noScaleCache: false,
            opacity: get(opacityStateStore) ? 0.4 : 1,
        });
        ellipse["id"] = "ellipse-" + new Date().getTime();

        canvas.add(ellipse);
    });

    canvas.on("mouse:move", function(o) {
        if (onPinchZoom(o)) {
            canvas.remove(ellipse);
            canvas.renderAll();
            return;
        }

        if (!isDown) {
            return;
        }
        const pointer = canvas.getPointer(o.e);
        let rx = Math.abs(origX - pointer.x) / 2;
        let ry = Math.abs(origY - pointer.y) / 2;
        const x = pointer.x;
        const y = pointer.y;

        if (rx > ellipse.strokeWidth) {
            rx -= ellipse.strokeWidth / 2;
        }
        if (ry > ellipse.strokeWidth) {
            ry -= ellipse.strokeWidth / 2;
        }

        if (x < origX) {
            ellipse.set({ originX: "right" });
        } else {
            ellipse.set({ originX: "left" });
        }

        if (y < origY) {
            ellipse.set({ originY: "bottom" });
        } else {
            ellipse.set({ originY: "top" });
        }

        ellipse.set({ rx: rx, ry: ry });

        canvas.renderAll();
    });

    canvas.on("mouse:up", function() {
        isDown = false;
        // probably changed from ellipse to rectangle
        if (!ellipse) {
            return;
        }
        if (ellipse.width < 5 || ellipse.height < 5) {
            canvas.remove(ellipse);
            ellipse = undefined;
            return;
        }

        if (ellipse.originX === "right") {
            ellipse.set({
                originX: "left",
                left: ellipse.left - ellipse.width + ellipse.strokeWidth,
            });
        }

        if (ellipse.originY === "bottom") {
            ellipse.set({
                originY: "top",
                top: ellipse.top - ellipse.height + ellipse.strokeWidth,
            });
        }

        ellipse.setCoords();
        canvas.setActiveObject(ellipse);
        undoStack.onObjectAdded(ellipse.id);
        ellipse = undefined;
    });
};
