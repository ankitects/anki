// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { BORDER_COLOR, disableRotation, SHAPE_MASK_COLOR, stopDraw } from "./lib";
import { objectAdded } from "./tool-undo-redo";

const addedRectangleIds: string[] = [];

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

        rect = new fabric.Rect({
            id: "rect-" + new Date().getTime(),
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
        });
        disableRotation(rect);
        canvas.add(rect);
    });

    canvas.on("mouse:move", function(o) {
        if (!isDown) return;
        const pointer = canvas.getPointer(o.e);
        let x = pointer.x;
        let y = pointer.y;

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

        // do not draw outside of canvas
        if (x < rect.strokeWidth) {
            x = -rect.strokeWidth + 0.5;
        }
        if (y < rect.strokeWidth) {
            y = -rect.strokeWidth + 0.5;
        }
        if (x >= canvas.width - rect.strokeWidth) {
            x = canvas.width - rect.strokeWidth + 0.5;
        }
        if (y >= canvas.height - rect.strokeWidth) {
            y = canvas.height - rect.strokeWidth + 0.5;
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
        objectAdded(canvas, addedRectangleIds, rect.id);
    });
};
