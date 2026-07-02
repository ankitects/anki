// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { fabric } from "fabric";

import { stopDraw } from "./lib";
import { onPinchZoom } from "./tool-zoom";

export const drawCursor = (canvas: fabric.Canvas): void => {
    canvas.selectionColor = "rgba(100, 100, 255, 0.3)";
    stopDraw(canvas);

    canvas.on("mouse:down", function(o) {
        if (o.target) {
            return;
        }
    });

    canvas.on("mouse:move", function(o) {
        if (onPinchZoom(o)) {
            return;
        }
    });
};
