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
        this.id = "ellipse-" + new Date().getTime();
    }

    toDataForCloze(): EllipseDataForCloze {
        return {
            ...super.toDataForCloze(),
            rx: floatToDisplay(this.rx),
            ry: floatToDisplay(this.ry),
        };
    }

    toFabric(size: Size): fabric.Ellipse {
        const absolute = this.toAbsolute(size);
        return new fabric.Ellipse(absolute);
    }

    toNormal(size: Size): Ellipse {
        return new Ellipse({
            ...this,
            ...super.normalPosition(size),
            rx: xToNormalized(size, this.rx),
            ry: yToNormalized(size, this.ry),
        });
    }

    toAbsolute(size: Size): Ellipse {
        return new Ellipse({
            ...this,
            ...super.absolutePosition(size),
            rx: xFromNormalized(size, this.rx),
            ry: yFromNormalized(size, this.ry),
        });
    }
}

interface EllipseDataForCloze extends ShapeDataForCloze {
    rx: string;
    ry: string;
}
