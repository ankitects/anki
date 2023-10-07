// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { disableRotation, stopDraw, TEXT_BORDER_COLOR } from "./lib";
import { undoStack } from "./tool-undo-redo";

export const drawText = (canvas: fabric.Canvas): void => {
    canvas.selectionColor = "rgba(0, 0, 0, 0)";
    stopDraw(canvas);

    canvas.on("mouse:down", function(o) {
        if (o.target) {
            return;
        }
        const pointer = canvas.getPointer(o.e);
        const text = new fabric.IText("text", {
            id: "text-" + new Date().getTime(),
            left: pointer.x,
            top: pointer.y,
            originX: "left",
            originY: "top",
            selectable: true,
            stroke: TEXT_BORDER_COLOR,
            strokeWidth: 1,
            noScaleCache: false,
        });
        disableRotation(text);
        canvas.add(text);
        undoStack.onObjectAdded(text.id);
    });
};
