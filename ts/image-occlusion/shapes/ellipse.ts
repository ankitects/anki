// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import type { ConstructorParams, Size } from "../types";
import type { ShapeDataForCloze } from "./base";
import { Shape } from "./base";
import { floatToDisplay } from "./floats";
import { xFromNormalized, xToNormalized, yFromNormalized, yToNormalized } from "./position";

export class Ellipse extends Shape {
    rx: number;
    ry: number;

    constructor({ rx = 0, ry = 0, ...rest }: ConstructorParams<Ellipse> = {}) {
        super(rest);
        this.rx = rx;
        this.ry = ry;
    }

    toDataForCloze(): EllipseDataForCloze {
        return {
            ...super.toDataForCloze(),
            rx: floatToDisplay(this.rx),
            ry: floatToDisplay(this.ry),
        };
    }

    toFabric(size: Size): fabric.Ellipse {
        this.makeAbsolute(size);
        return new fabric.Ellipse(this);
    }

    makeNormal(size: Size): void {
        super.makeNormal(size);
        this.rx = xToNormalized(size, this.rx);
        this.ry = yToNormalized(size, this.ry);
    }

    makeAbsolute(size: Size): void {
        super.makeAbsolute(size);
        this.rx = xFromNormalized(size, this.rx);
        this.ry = yFromNormalized(size, this.ry);
    }
}

interface EllipseDataForCloze extends ShapeDataForCloze {
    rx: string;
    ry: string;
}
