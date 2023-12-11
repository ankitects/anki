// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Canvas, Object as FabricObject } from "fabric";
import { fabric } from "fabric";
import { cloneDeep } from "lodash-es";

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
    let index = 0;
    shapes.forEach((shapeOrShapes) => {
        clozes += shapeOrShapesToCloze(shapeOrShapes, index, occludeInactive);
        if (!(shapeOrShapes instanceof Text)) {
            index++;
        }
    });

    return { clozes, noteCount: index };
}

/** Gather all Fabric shapes, and convert them into BaseShapes or
 * BaseShape[]s.
 */
export function baseShapesFromFabric(): ShapeOrShapes[] {
    const canvas = globalThis.canvas as Canvas;
    const activeObject = canvas.getActiveObject();
    const selectionContainingMultipleObjects = activeObject instanceof fabric.ActiveSelection
            && (activeObject.size() > 1)
        ? activeObject
        : null;
    const objects = canvas.getObjects() as FabricObject[];
    return objects
        .map((object) => {
            // If the object is in the active selection containing multiple objects,
            // we need to calculate its x and y coordinates relative to the canvas.
            const parent = selectionContainingMultipleObjects?.contains(object)
                ? selectionContainingMultipleObjects
                : undefined;
            return fabricObjectToBaseShapeOrShapes(
                canvas,
                object,
                parent,
            );
        })
        .filter((o): o is ShapeOrShapes => o !== null);
}

/** Convert a single Fabric object/group to one or more BaseShapes. */
function fabricObjectToBaseShapeOrShapes(
    size: Size,
    object: FabricObject,
    parentObject?: FabricObject,
): ShapeOrShapes | null {
    let shape: Shape;

    // Prevents the original fabric object from mutating when a non-primitive
    // property of a Shape mutates.
    const cloned = cloneDeep(object);
    if (parentObject) {
        const scaling = parentObject.getObjectScaling();
        cloned.width = cloned.width * scaling.scaleX;
        cloned.height = cloned.height * scaling.scaleY;
    }

    switch (object.type) {
        case "rect":
            shape = new Rectangle(cloned);
            break;
        case "ellipse":
            shape = new Ellipse(cloned);
            break;
        case "polygon":
            shape = new Polygon(cloned);
            break;
        case "i-text":
            shape = new Text(cloned);
            break;
        case "group":
            return object._objects.map((child) => {
                return fabricObjectToBaseShapeOrShapes(
                    size,
                    child,
                    object,
                );
            });
        default:
            return null;
    }
    if (parentObject) {
        const newPosition = fabric.util.transformPoint(
            { x: shape.left, y: shape.top },
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
    index: number,
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
            .map((shape) => shapeOrShapesToCloze(shape, index, occludeInactive))
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

    let ordinal: number;
    if (type === "text") {
        ordinal = 0;
    } else {
        ordinal = index + 1;
    }
    shapeOrShapes.ordinal = ordinal;
    text = `{{c${ordinal}::image-occlusion:${type}${text}}}<br>`;

    return text;
}
