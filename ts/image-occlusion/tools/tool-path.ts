// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";
import { get } from "svelte/store";

import { pathToolConfig } from "../store";
import { stopDraw } from "./lib";

export const drawPath = (canvas: fabric.Canvas): void => {
    canvas.selectionColor = "rgba(0, 0, 0, 0)";
    stopDraw(canvas);

    const brush = new fabric.PencilBrush(canvas);
    canvas.isDrawingMode = true;
    canvas.freeDrawingBrush = brush;

    canvas.on("mouse:down", function() {
        brush.color = get(pathToolConfig).color;
        brush.width = get(pathToolConfig).size;
    });
};
