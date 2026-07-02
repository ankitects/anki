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
    if (fabricShape.type === "i-text") {
        enableUniformScaling(canvas, fabricShape);
    } else {
        // No border around i-text shapes since it will be interpretted
        // as character stroke, this is supposed to create an outline
        // around the entire shape.
        addBorder(fabricShape);
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
