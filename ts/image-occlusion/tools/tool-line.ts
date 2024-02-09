// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { stopDraw } from "./lib";
import { getAnnotationConfig } from "./lib";
import { undoStack } from "./tool-undo-redo";

export const drawLine = (canvas: fabric.Canvas): void => {
    canvas.selectionColor = "rgba(0, 0, 0, 0)";
    let isDown = false;
    let line, origX, origY;

    stopDraw(canvas);

    canvas.on("mouse:down", function(o) {
        if (o.target) {
            return;
        }
        isDown = true;
        const config = getAnnotationConfig("draw-line");

        const pointer = canvas.getPointer(o.e);
        origX = pointer.x;
        origY = pointer.y;

        const points = [pointer.x, pointer.y, pointer.x, pointer.y];
        line = new fabric.Line(points, {
            id: "line-" + new Date().getTime(),
            left: origX,
            top: origY,
            originX: "left",
            originY: "top",
            transparentCorners: false,
            selectable: true,
            stroke: config.color,
            strokeWidth: config.size,
            strokeUniform: true,
            noScaleCache: false,
        });
        canvas.add(line);
    });

    canvas.on("mouse:move", function(o) {
        if (!isDown) {
            return;
        }

        const pointer = canvas.getPointer(o.e);
        line.set({ x2: pointer.x, y2: pointer.y });
        canvas.renderAll();
    });

    canvas.on("mouse:up", function() {
        isDown = false;

        if (!line) {
            return;
        }

        line.setCoords();
        canvas.setActiveObject(line);
        undoStack.onObjectAdded(line.id);
        line = undefined;
    });
};
