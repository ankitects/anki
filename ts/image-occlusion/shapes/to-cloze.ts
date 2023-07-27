// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Canvas, Object as FabricObject } from "fabric";

import { makeMaskTransparent } from "../tools/lib";
import type { Size } from "../types";
import type { Shape, ShapeOrShapes } from "./base";
import { Ellipse } from "./ellipse";
import { Polygon } from "./polygon";
import { Rectangle } from "./rectangle";

export function exportShapesToClozeDeletions(occludeInactive: boolean): { clozes: string; noteCount: number } {
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
    return objects.map((object) => {
        return fabricObjectToBaseShapeOrShapes(canvas, object, occludeInactive);
    }).filter((o): o is ShapeOrShapes => o !== null);
}

interface TopAndLeftOffset {
    top: number;
    left: number;
}

/** Convert a single Fabric object/group to one or more BaseShapes. */
function fabricObjectToBaseShapeOrShapes(
    size: Size,
    object: FabricObject,
    occludeInactive: boolean,
    groupOffset: TopAndLeftOffset = { top: 0, left: 0 },
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
            // Positions inside a group are relative to the group, so we
            // need to pass in an offset. We do not support nested groups.
            groupOffset = {
                left: object.left + object.width / 2,
                top: object.top + object.height / 2,
            };
            return object._objects.map((obj) => {
                return fabricObjectToBaseShapeOrShapes(size, obj, occludeInactive, groupOffset);
            });
        default:
            return null;
    }
    shape.occludeInactive = occludeInactive;
    shape.left += groupOffset.left;
    shape.top += groupOffset.top;
    shape.makeNormal(size);
    return shape;
}

/** generate cloze data in form of
 {{c1::image-occlusion:rect:top=.1:left=.23:width=.4:height=.5}} */
function shapeOrShapesToCloze(shapeOrShapes: ShapeOrShapes, index: number): string {
    let text = "";
    function addKeyValue(key: string, value: string) {
        if (Number.isNaN(Number(value))) {
            value = ".0000";
        }
        text += `:${key}=${value}`;
    }

    let type: string;
    if (Array.isArray(shapeOrShapes)) {
        return shapeOrShapes.map((shape) => shapeOrShapesToCloze(shape, index)).join("");
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
