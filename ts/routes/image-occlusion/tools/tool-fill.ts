// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { get, type Readable } from "svelte/store";
import { findTargetInGroup, stopDraw } from "./lib";
import { undoStack } from "./tool-undo-redo";

export const fillMask = (canvas: fabric.Canvas, colourStore: Readable<string>): void => {
    // remove selectable for shapes
    canvas.discardActiveObject();
    canvas.forEachObject(function(o) {
        o.selectable = false;
    });
    canvas.selectionColor = "rgba(0, 0, 0, 0)";
    stopDraw(canvas);

    canvas.on("mouse:down", function(o) {
        const target = o.target instanceof fabric.Group
            ? findTargetInGroup(o.target, canvas.getPointer(o.e) as fabric.Point)
            : o.target;
        const colour = get(colourStore);
        if (!target || target.fill === colour) { return; }
        target.fill = colour;
        undoStack.onObjectModified();
    });
};
