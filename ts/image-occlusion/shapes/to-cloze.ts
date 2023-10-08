// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Canvas, Object as FabricObject } from "fabric";
import { fabric } from "fabric";

import { makeMaskTransparent } from "../tools/lib";
import type { Size } from "../types";
import type { Shape, ShapeOrShapes } from "./base";
import { Ellipse } from "./ellipse";
import { Polygon } from "./polygon";
import { Rectangle } from "./rectangle";

export function exportShapesToClozeDeletions(occludeInactive: boolean): {
    clozes: string;
    noteCount: number;
} {
    const shapes = baseShapesFromFabric(occludeInactive);

    let clozes = "";
    shapes.forEach((shapeOrShapes, index) => {
        clozes += shapeOrShapesToCloze(shapeOrShapes, index);
    });

    return { clozes, noteCount: shapes.length };
}

/** Gather all Fabric shapes, and convert them into BaseShapes or
 * BaseShape[]s.
 */
function baseShapesFromFabric(occludeInactive: boolean): ShapeOrShapes[] {
    const canvas = globalThis.canvas as Canvas;
    makeMaskTransparent(canvas, false);
    const objects = canvas.getObjects() as FabricObject[];
    return objects
        .map((object) => {
            return fabricObjectToBaseShapeOrShapes(
                canvas,
                object,
                occludeInactive,
            );
        })
        .filter((o): o is ShapeOrShapes => o !== null);
}

/** Convert a single Fabric object/group to one or more BaseShapes. */
function fabricObjectToBaseShapeOrShapes(
    size: Size,
    object: FabricObject,
    occludeInactive: boolean,
    parentObject?: FabricObject,
): ShapeOrShapes | null {
    let shape: Shape;

    switch (object.type) {
        case "rect":
            shape = new Rectangle(object);
            break;
        case "ellipse":
            shape = new Ellipse(object);
            break;
        case "polygon":
            shape = new Polygon(object);
            break;
        case "group":
            return object._objects.map((child) => {
                return fabricObjectToBaseShapeOrShapes(
                    size,
                    child,
                    occludeInactive,
                    object,
                );
            });
        default:
            return null;
    }
    shape.occludeInactive = occludeInactive;
    if (parentObject) {
        const newPosition = fabric.util.transformPoint(
            { x: shape.left, y: shape.top },
            parentObject.calcTransformMatrix(),
        );
        shape.left = newPosition.x;
        shape.top = newPosition.y;
    }

    shape.makeNormal(size);
    return shape;
}

/** generate cloze data in form of
 {{c1::image-occlusion:rect:top=.1:left=.23:width=.4:height=.5}} */
function shapeOrShapesToCloze(
    shapeOrShapes: ShapeOrShapes,
    index: number,
): string {
    let text = "";
    function addKeyValue(key: string, value: string) {
        if (key !== "points" && Number.isNaN(Number(value))) {
            value = ".0000";
        }
        text += `:${key}=${value}`;
    }

    let type: string;
    if (Array.isArray(shapeOrShapes)) {
        return shapeOrShapes
            .map((shape) => shapeOrShapesToCloze(shape, index))
            .join("");
    } else if (shapeOrShapes instanceof Rectangle) {
        type = "rect";
    } else if (shapeOrShapes instanceof Ellipse) {
        type = "ellipse";
    } else if (shapeOrShapes instanceof Polygon) {
        type = "polygon";
    } else {
        throw new Error("Unknown shape type");
    }

    for (const [key, value] of Object.entries(shapeOrShapes.toDataForCloze())) {
        addKeyValue(key, value);
    }

    text = `{{c${index + 1}::image-occlusion:${type}${text}}}<br>`;
    return text;
}
