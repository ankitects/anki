// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { getAnnotationConfig, stopDraw } from "./lib";
import { undoStack } from "./tool-undo-redo";

export const drawPath = (canvas: fabric.Canvas): void => {
    canvas.selectionColor = "rgba(0, 0, 0, 0)";
    stopDraw(canvas);

    let config = getAnnotationConfig("draw-path");
    const brush = new fabric.PencilBrush(canvas);
    canvas.isDrawingMode = true;
    canvas.freeDrawingBrush = brush;

    canvas.on("mouse:down", () => {
        config = getAnnotationConfig("draw-path");
        brush.color = config.color;
        brush.width = config.size;
    });

    canvas.on("path:created", () => {
        undoStack.onObjectModified();
    });
};
