// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";
import { get } from "svelte/store";

import { opacityStateStore, textEditingState } from "../store";
import {
    enableUniformScaling,
    isPointerInBoundingBox,
    stopDraw,
    TEXT_BACKGROUND_COLOR,
    TEXT_FONT_FAMILY,
    TEXT_PADDING,
} from "./lib";
import { undoStack } from "./tool-undo-redo";
import { onPinchZoom } from "./tool-zoom";

export const drawText = (canvas: fabric.Canvas): void => {
    canvas.selectionColor = "rgba(0, 0, 0, 0)";
    stopDraw(canvas);

    let text;

    canvas.on("mouse:down", function(o) {
        if (o.target) {
            return;
        }
        const pointer = canvas.getPointer(o.e);

        if (!isPointerInBoundingBox(pointer)) {
            return;
        }

        text = new fabric.IText("text", {
            left: pointer.x,
            top: pointer.y,
            originX: "left",
            originY: "top",
            selectable: true,
            strokeWidth: 1,
            noScaleCache: false,
            fontFamily: TEXT_FONT_FAMILY,
            backgroundColor: TEXT_BACKGROUND_COLOR,
            padding: TEXT_PADDING,
            opacity: get(opacityStateStore) ? 0.4 : 1,
            lineHeight: 1,
        });
        text["id"] = "text-" + new Date().getTime();

        enableUniformScaling(canvas, text);
        canvas.add(text);
        canvas.setActiveObject(text);
        undoStack.onObjectAdded(text.id);
        text.selectAll();
    });

    canvas.on("mouse:move", function(o) {
        if (onPinchZoom(o)) {
            canvas.remove(text);
            canvas.renderAll();
            return;
        }
    });

    canvas.on("text:editing:entered", function() {
        textEditingState.set(true);
    });

    canvas.on("text:editing:exited", function() {
        textEditingState.set(false);
    });
};
