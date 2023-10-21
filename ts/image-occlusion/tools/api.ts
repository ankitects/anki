// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { fabric } from "fabric";

import type { Shape } from "../shapes";
import { addShape, addShapeGroup } from "./from-shapes";
import { clear, redraw } from "./lib";

export class MaskEditorAPI {
    canvas: fabric.Canvas;

    constructor(canvas) {
        this.canvas = canvas;
    }

    addShape(shape: Shape): void {
        addShape(this.canvas, shape);
    }

    addShapeGroup(shapes: Shape[]): void {
        addShapeGroup(this.canvas, shapes);
    }

    redraw(): void {
        redraw(this.canvas);
    }

    clear(): void {
        clear(this.canvas);
    }
}
