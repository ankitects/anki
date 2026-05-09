// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { fabric } from "fabric";

import type { ShapeOrShapes } from "../shapes";
import { Ellipse, Polygon, Rectangle, Shape, Text } from "../shapes";
import { baseShapesFromFabric, exportShapesToClozeDeletions } from "../shapes/to-cloze";
import { addShape, addShapeGroup } from "./from-shapes";
import { clear, redraw } from "./lib";

interface ClozeExportResult {
    clozes: string;
    cardCount: number;
}

export class MaskEditorAPI {
    readonly Shape = Shape;
    readonly Rectangle = Rectangle;
    readonly Ellipse = Ellipse;
    readonly Polygon = Polygon;
    readonly Text = Text;

    readonly canvas: fabric.Canvas;

    constructor(canvas) {
        this.canvas = canvas;
    }

    addShape(bounding, shape: Shape): void {
        addShape(this.canvas, bounding, shape);
    }

    addShapeGroup(bounding, shapes: Shape[]): void {
        addShapeGroup(this.canvas, bounding, shapes);
    }

    getClozes(occludeInactive: boolean): ClozeExportResult {
        const { clozes, noteCount: cardCount } = exportShapesToClozeDeletions(occludeInactive);
        return { clozes, cardCount };
    }

    getShapes(): ShapeOrShapes[] {
        return baseShapesFromFabric();
    }

    redraw(): void {
        redraw(this.canvas);
    }

    clear(): void {
        clear(this.canvas);
    }
}
