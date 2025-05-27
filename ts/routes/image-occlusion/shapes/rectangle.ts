// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import type { ConstructorParams, Size } from "../types";
import type { ShapeDataForCloze } from "./base";
import { Shape } from "./base";
import { floatToDisplay } from "./lib";
import { xFromNormalized, xToNormalized, yFromNormalized, yToNormalized } from "./position";

export class Rectangle extends Shape {
    width: number;
    height: number;

    constructor({ width = 0, height = 0, ...rest }: ConstructorParams<Rectangle> = {}) {
        super(rest);
        this.width = width;
        this.height = height;
        this.id = "rect-" + new Date().getTime();
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
            ...super.normalPosition(size),
            width: xToNormalized(size, this.width),
            height: yToNormalized(size, this.height),
        });
    }

    toAbsolute(size: Size): Rectangle {
        return new Rectangle({
            ...this,
            ...super.absolutePosition(size),
            width: xFromNormalized(size, this.width),
            height: yFromNormalized(size, this.height),
        });
    }
}

interface RectangleDataForCloze extends ShapeDataForCloze {
    width: string;
    height: string;
}
