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
        this.makeAbsolute(size);
        return new fabric.Rect(this);
    }

    makeNormal(size: Size): void {
        super.makeNormal(size);
        this.width = xToNormalized(size, this.width);
        this.height = yToNormalized(size, this.height);
    }

    makeAbsolute(size: Size): void {
        super.makeAbsolute(size);
        this.width = xFromNormalized(size, this.width);
        this.height = yFromNormalized(size, this.height);
    }
}

interface RectangleDataForCloze extends ShapeDataForCloze {
    width: string;
    height: string;
}
