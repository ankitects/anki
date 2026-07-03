// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { SHAPE_MASK_COLOR } from "../tools/lib";
import type { ConstructorParams, Size } from "../types";
import { angleToStored, floatToDisplay } from "./lib";
import { xFromNormalized, xToNormalized, yFromNormalized, yToNormalized } from "./position";

export type ShapeOrShapes = Shape | Shape[];

/** Defines a basic shape that can have its coordinates stored in either
    absolute pixels (relative to a containing canvas), or in normalized 0-1
    form. Can be converted to a fabric object, or to a format suitable for
    storage in a cloze note.
*/
export class Shape {
    left: number;
    top: number;
    angle?: number; // polygons don't use it
    fill: string;
    /** Whether occlusions from other cloze numbers should be shown on the
     * question side. Used only in reviewer code.
     */
    occludeInactive?: boolean;
    /* Cloze ordinal */
    ordinal: number | undefined;
    id: string | undefined;

    constructor(
        { left = 0, top = 0, angle = 0, fill = SHAPE_MASK_COLOR, occludeInactive, ordinal = undefined }:
            ConstructorParams<Shape> = {},
    ) {
        this.left = left;
        this.top = top;
        this.angle = angle;
        this.fill = fill;
        this.occludeInactive = occludeInactive;
        this.ordinal = ordinal;
    }

    /** Format numbers and remove default values, for easier serialization to
     * text.
     */
    toDataForCloze(): ShapeDataForCloze {
        const angle = angleToStored(this.angle);
        return {
            left: floatToDisplay(this.left),
            top: floatToDisplay(this.top),
            ...(!angle ? {} : { angle: angle.toString() }),
        };
    }

    toFabric(size: Size): fabric.Object {
        const absolute = this.toAbsolute(size);
        return new fabric.Object(absolute);
    }

    normalPosition(size: Size) {
        return {
            left: xToNormalized(size, this.left),
            top: yToNormalized(size, this.top),
        };
    }

    toNormal(size: Size): Shape {
        return new Shape({
            ...this,
            ...this.normalPosition(size),
        });
    }

    absolutePosition(size: Size) {
        return {
            left: xFromNormalized(size, this.left),
            top: yFromNormalized(size, this.top),
        };
    }

    toAbsolute(size: Size): Shape {
        return new Shape({
            ...this,
            ...this.absolutePosition(size),
        });
    }
}

export interface ShapeDataForCloze {
    left: string;
    top: string;
    angle?: string;
    fill?: string;
    oi?: string;
}
