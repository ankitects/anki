// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";
import { cloneDeep } from "lodash-es";

import { getBoundingBoxSize } from "../tools/lib";
import type { Size } from "../types";
import type { Shape, ShapeOrShapes } from "./base";
import { Ellipse } from "./ellipse";
import { Polygon } from "./polygon";
import { Rectangle } from "./rectangle";
import { Text } from "./text";

export function exportShapesToClozeDeletions(occludeInactive: boolean): {
    clozes: string;
    noteCount: number;
} {
    const shapes = baseShapesFromFabric();

    let clozes = "";
    let noteCount = 0;

    // take out all ordinal values from shapes
    const ordinalList = shapes.map((shape) => {
        if (Array.isArray(shape)) {
            return shape[0].ordinal;
        } else {
            return shape.ordinal;
        }
    });

    const filterOrdinalList: number[] = ordinalList.flatMap(v => typeof v === "number" ? [v] : []);
    const maxOrdinal = Math.max(...filterOrdinalList, 0);

    const missingOrdinals: number[] = [];
    for (let i = 1; i <= maxOrdinal; i++) {
        if (!ordinalList.includes(i)) {
            missingOrdinals.push(i);
        }
    }

    let nextOrdinal = maxOrdinal + 1;

    shapes.map((shapeOrShapes) => {
        if (shapeOrShapes === null) {
            return;
        }

        // Maintain existing ordinal in editing mode
        let ordinal: number | undefined;
        if (Array.isArray(shapeOrShapes)) {
            ordinal = shapeOrShapes[0].ordinal;
        } else {
            ordinal = shapeOrShapes.ordinal;
        }

        if (ordinal === undefined) {
            // if ordinal is undefined, assign a missing ordinal if available
            if (shapeOrShapes instanceof Text) {
                ordinal = 0;
            } else if (missingOrdinals.length > 0) {
                ordinal = missingOrdinals.shift()!;
            } else {
                ordinal = nextOrdinal;
                nextOrdinal++;
            }

            if (Array.isArray(shapeOrShapes)) {
                shapeOrShapes.forEach((shape) => (shape.ordinal = ordinal));
            } else {
                shapeOrShapes.ordinal = ordinal;
            }
        }

        clozes += shapeOrShapesToCloze(
            shapeOrShapes,
            ordinal,
            occludeInactive,
        );

        if (!(shapeOrShapes instanceof Text)) {
            noteCount++;
        }
    });
    return { clozes, noteCount };
}

/** Gather all Fabric shapes, and convert them into BaseShapes or
 * BaseShape[]s.
 */
export function baseShapesFromFabric(): ShapeOrShapes[] {
    const canvas = globalThis.canvas as fabric.Canvas;
    const activeObject = canvas.getActiveObject();
    const selectionContainingMultipleObjects = activeObject instanceof fabric.ActiveSelection
            && (activeObject.size() > 1)
        ? activeObject
        : null;
    const objects = canvas.getObjects();
    const boundingBox = getBoundingBoxSize();
    // filter transparent rectangles
    return objects
        .map((object) => {
            // If the object is in the active selection containing multiple objects,
            // we need to calculate its x and y coordinates relative to the canvas.
            const parent = selectionContainingMultipleObjects?.contains(object)
                ? selectionContainingMultipleObjects
                : undefined;
            // shapes with width or height less than 5 are not valid
            // if shape is Rect and fill is transparent, skip it
            if (object.width! < 5 || object.height! < 5 || object.fill == "transparent") {
                return null;
            }
            return fabricObjectToBaseShapeOrShapes(
                boundingBox,
                object,
                parent,
            );
        })
        .filter((o): o is ShapeOrShapes => o !== null);
}

/** Convert a single Fabric object/group to one or more BaseShapes. */
function fabricObjectToBaseShapeOrShapes(
    size: Size,
    object: fabric.Object,
    parentObject?: fabric.Object,
): ShapeOrShapes | null {
    let shape: Shape;

    // Prevents the original fabric object from mutating when a non-primitive
    // property of a Shape mutates.
    const cloned = cloneDeep(object);
    if (parentObject) {
        const scaling = parentObject.getObjectScaling();
        cloned.width = cloned.width! * scaling.scaleX;
        cloned.height = cloned.height! * scaling.scaleY;
    }

    switch (object.type) {
        case "rect":
            shape = new Rectangle(cloned as any);
            break;
        case "ellipse":
            shape = new Ellipse(cloned as any);
            break;
        case "polygon":
            shape = new Polygon(cloned as any);
            break;
        case "i-text":
            shape = new Text(cloned as any);
            break;
        case "group":
            return (object as fabric.Group).getObjects().flatMap((child) => {
                return fabricObjectToBaseShapeOrShapes(
                    size,
                    child,
                    object,
                )!;
            });
        default:
            return null;
    }
    if (parentObject) {
        const newPosition = fabric.util.transformPoint(
            new fabric.Point(shape.left, shape.top),
            parentObject.calcTransformMatrix(),
        );
        shape.left = newPosition.x;
        shape.top = newPosition.y;
    }

    shape = shape.toNormal(size);
    return shape;
}

/** generate cloze data in form of
 {{c1::image-occlusion:rect:top=.1:left=.23:width=.4:height=.5}} */
function shapeOrShapesToCloze(
    shapeOrShapes: ShapeOrShapes,
    ordinal: number,
    occludeInactive: boolean,
): string {
    let text = "";
    function addKeyValue(key: string, value: string) {
        value = value.replace(":", "\\:");
        text += `:${key}=${value}`;
    }

    let type: string;
    if (Array.isArray(shapeOrShapes)) {
        return shapeOrShapes
            .map((shape) => shapeOrShapesToCloze(shape, ordinal, occludeInactive))
            .join("");
    } else if (shapeOrShapes instanceof Rectangle) {
        type = "rect";
    } else if (shapeOrShapes instanceof Ellipse) {
        type = "ellipse";
    } else if (shapeOrShapes instanceof Polygon) {
        type = "polygon";
    } else if (shapeOrShapes instanceof Text) {
        type = "text";
    } else {
        throw new Error("Unknown shape type");
    }

    for (const [key, value] of Object.entries(shapeOrShapes.toDataForCloze())) {
        addKeyValue(key, value);
    }
    if (occludeInactive) {
        addKeyValue("oi", "1");
    }

    text = `{{c${ordinal}::image-occlusion:${type}${text}}}<br>`;

    return text;
}
