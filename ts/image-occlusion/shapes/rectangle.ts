// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import type { ConstructorParams, Size } from "../types";
import type { ShapeDataForCloze } from "./base";
import { Shape } from "./base";
import { floatToDisplay } from "./floats";
import { xFromNormalized, xToNormalized, yFromNormalized, yToNormalized } from "./position";

export class Rectangle extends Shape {
    width: number;
    height: number;

    constructor({ width = 0, height = 0, ...rest }: ConstructorParams<Rectangle> = {}) {
        super(rest);
        this.width = width;
        this.height = height;
    }

    toDataForCloze(): RectangleDataForCloze {
        return {
            ...super.toDataForCloze(),
            width: floatToDisplay(this.width),
            height: floatToDisplay(this.height),
        };
    }

    toFabric(size: Size): fabric.Rect {
        const absolute = this.toAbsolute(size);
        return new fabric.Rect(absolute);
    }

    toNormal(size: Size): Rectangle {
        return new Rectangle({
            ...this,
            width: xToNormalized(size, this.width),
            height: yToNormalized(size, this.height),
            left: xToNormalized(size, this.left),
            top: yToNormalized(size, this.top),
        });
    }

    toAbsolute(size: Size): Rectangle {
        return new Rectangle({
            ...this,
            width: xFromNormalized(size, this.width),
            height: yFromNormalized(size, this.height),
            left: xFromNormalized(size, this.left),
            top: yFromNormalized(size, this.top),
        });
    }
}

interface RectangleDataForCloze extends ShapeDataForCloze {
    width: string;
    height: string;
}
