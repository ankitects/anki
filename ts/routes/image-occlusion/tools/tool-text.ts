// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";
import { get } from "svelte/store";

import type { Callback } from "@tslib/helpers";
import { opacityStateStore } from "../store";
import {
    enableUniformScaling,
    isPointerInBoundingBox,
    stopDraw,
    TEXT_BACKGROUND_COLOR,
    TEXT_COLOR,
    TEXT_FONT_FAMILY,
    TEXT_PADDING,
} from "./lib";
import { undoStack } from "./tool-undo-redo";
import { onPinchZoom } from "./tool-zoom";

export const drawText = (canvas: fabric.Canvas, onActivated: Callback): void => {
    canvas.selectionColor = "rgba(0, 0, 0, 0)";
    stopDraw(canvas);

    let text: fabric.IText;

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
            fill: TEXT_COLOR,
            fontFamily: TEXT_FONT_FAMILY,
            backgroundColor: TEXT_BACKGROUND_COLOR,
            padding: TEXT_PADDING,
            opacity: get(opacityStateStore) ? 0.4 : 1,
            lineHeight: 1,
            lockScalingFlip: true,
        });
        text["id"] = "text-" + new Date().getTime();

        enableUniformScaling(canvas, text);
        canvas.add(text);
        canvas.setActiveObject(text);
        undoStack.onObjectAdded(text.id);
        text.enterEditing();
        text.selectAll();
        onActivated();
    });

    canvas.on("mouse:move", function(o) {
        if (onPinchZoom(o)) {
            canvas.remove(text);
            canvas.renderAll();
            return;
        }
    });
};
