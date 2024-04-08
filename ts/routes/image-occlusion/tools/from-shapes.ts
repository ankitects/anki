// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import type { Shape } from "../shapes";
import { addBorder, enableUniformScaling } from "./lib";

export const addShape = (
    canvas: fabric.Canvas,
    boundingBox: fabric.Rect,
    shape: Shape,
): void => {
    const fabricShape = shape.toFabric(boundingBox.getBoundingRect(true));
    addBorder(fabricShape);
    if (fabricShape.type === "i-text") {
        enableUniformScaling(canvas, fabricShape);
    }
    canvas.add(fabricShape);
};

export const addShapeGroup = (
    canvas: fabric.Canvas,
    boundingBox: fabric.Rect,
    shapes: Shape[],
): void => {
    const group = new fabric.Group();
    shapes.map((shape) => {
        const fabricShape = shape.toFabric(boundingBox.getBoundingRect(true));
        addBorder(fabricShape);
        group.addWithUpdate(fabricShape);
    });
    canvas.add(group);
};
