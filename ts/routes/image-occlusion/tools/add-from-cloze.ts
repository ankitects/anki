// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { GetImageOcclusionNoteResponse_ImageOcclusion } from "@generated/anki/image_occlusion_pb";
import type { fabric } from "fabric";

import { extractShapesFromClozedField } from "../shapes/from-cloze";
import { addShape, addShapeGroup } from "./from-shapes";
import { redraw } from "./lib";

export const addShapesToCanvasFromCloze = (
    canvas: fabric.Canvas,
    boundingBox: fabric.Rect,
    occlusions: GetImageOcclusionNoteResponse_ImageOcclusion[],
): void => {
    for (const shapeOrShapes of extractShapesFromClozedField(occlusions)) {
        if (Array.isArray(shapeOrShapes)) {
            addShapeGroup(canvas, boundingBox, shapeOrShapes);
        } else {
            addShape(canvas, boundingBox, shapeOrShapes);
        }
    }
    redraw(canvas);
};
