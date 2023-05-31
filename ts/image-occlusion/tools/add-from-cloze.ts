// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";
import { extractShapesFromClozedField } from "image-occlusion/shapes/from-cloze";

import type { Size } from "../types";
import { addBorder, disableRotation } from "./lib";

export const addShapesToCanvasFromCloze = (canvas: fabric.Canvas, clozeStr: string): void => {
    const size: Size = canvas;
    for (const shapeOrShapes of extractShapesFromClozedField(clozeStr)) {
        if (Array.isArray(shapeOrShapes)) {
            const group = new fabric.Group();
            shapeOrShapes.map((shape) => {
                const fabricShape = shape.toFabric(size);
                addBorder(fabricShape);
                group.addWithUpdate(fabricShape);
                disableRotation(group);
            });
            canvas.add(group);
        } else {
            const shape = shapeOrShapes.toFabric(size);
            addBorder(shape);
            disableRotation(shape);
            canvas.add(shape);
        }
    }
    canvas.requestRenderAll();
};
