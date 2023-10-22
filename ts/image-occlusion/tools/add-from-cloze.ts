// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { GetImageOcclusionNoteResponse_ImageOcclusion } from "@tslib/anki/image_occlusion_pb";
import type { fabric } from "fabric";
import { extractShapesFromClozedField } from "image-occlusion/shapes/from-cloze";

import { addShape, addShapeGroup } from "./from-shapes";
import { redraw } from "./lib";

export const addShapesToCanvasFromCloze = (
    canvas: fabric.Canvas,
    occlusions: GetImageOcclusionNoteResponse_ImageOcclusion[],
): void => {
    for (const shapeOrShapes of extractShapesFromClozedField(occlusions)) {
        if (Array.isArray(shapeOrShapes)) {
            addShapeGroup(canvas, shapeOrShapes);
        } else {
            addShape(canvas, shapeOrShapes);
        }
    }
    redraw(canvas);
};
