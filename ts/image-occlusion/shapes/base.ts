// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { SHAPE_MASK_COLOR } from "../tools/lib";
import type { ConstructorParams, Size } from "../types";
import { floatToDisplay } from "./floats";
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
    fill: string = SHAPE_MASK_COLOR;
    /** Whether occlusions from other cloze numbers should be shown on the
     * question side.
     */
    occludeInactive = false;

    constructor(
        { left = 0, top = 0, fill = SHAPE_MASK_COLOR, occludeInactive = false }: ConstructorParams<Shape> = {},
    ) {
        this.left = left;
        this.top = top;
        this.fill = fill;
        this.occludeInactive = occludeInactive;
    }

    /** Format numbers and remove default values, for easier serialization to
     * text.
     */
    toDataForCloze(): ShapeDataForCloze {
        return {
            left: floatToDisplay(this.left),
            top: floatToDisplay(this.top),
            ...(this.fill === SHAPE_MASK_COLOR ? {} : { fill: this.fill }),
            ...(this.occludeInactive ? { oi: "1" } : {}),
        };
    }

    toFabric(size: Size): fabric.ForCloze {
        this.makeAbsolute(size);
        return new fabric.Object(this);
    }

    makeNormal(size: Size): void {
        this.left = xToNormalized(size, this.left);
        this.top = yToNormalized(size, this.top);
    }

    makeAbsolute(size: Size): void {
        this.left = xFromNormalized(size, this.left);
        this.top = yFromNormalized(size, this.top);
    }
}

export interface ShapeDataForCloze {
    left: string;
    top: string;
    fill?: string;
    oi?: string;
}
