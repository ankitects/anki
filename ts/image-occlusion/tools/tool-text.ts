// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import {
    disableRotation,
    enableUniformScaling,
    SHAPE_MASK_COLOR,
    stopDraw,
    TEXT_FONT_FAMILY,
    TEXT_PADDING,
} from "./lib";
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
            strokeWidth: 1,
            noScaleCache: false,
            fontFamily: TEXT_FONT_FAMILY,
            backgroundColor: SHAPE_MASK_COLOR,
            padding: TEXT_PADDING,
        });
        disableRotation(text);
        enableUniformScaling(canvas, text);
        canvas.add(text);
        undoStack.onObjectAdded(text.id);
    });
};
