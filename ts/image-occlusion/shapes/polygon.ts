// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import type { ConstructorParams, Size } from "../types";
import type { ShapeDataForCloze } from "./base";
import { Shape } from "./base";
import { floatToDisplay } from "./floats";
import { xFromNormalized, xToNormalized, yFromNormalized, yToNormalized } from "./position";

export class Polygon extends Shape {
    points: Point[];

    constructor({ points = [], ...rest }: ConstructorParams<Polygon> = {}) {
        super(rest);
        this.points = points;
    }

    toDataForCloze(): PolygonDataForCloze {
        return {
            ...super.toDataForCloze(),
            points: this.points.map(({ x, y }) => `${floatToDisplay(x)},${floatToDisplay(y)}`).join(" "),
        };
    }

    toFabric(size: Size): fabric.Polygon {
        this.makeAbsolute(size);
        return new fabric.Polygon(this.points, this);
    }

    makeNormal(size: Size): void {
        super.makeNormal(size);
        this.points.forEach((p) => {
            p.x = xToNormalized(size, p.x);
            p.y = yToNormalized(size, p.y);
        });
    }

    makeAbsolute(size: Size): void {
        super.makeAbsolute(size);
        this.points.forEach((p) => {
            p.x = xFromNormalized(size, p.x);
            p.y = yFromNormalized(size, p.y);
        });
    }
}

interface PolygonDataForCloze extends ShapeDataForCloze {
    // "x1,y1 x2,y2 ...""
    points: string;
}

export class Point {
    x = 0;
    y = 0;

    constructor({ x = 0, y = 0 }: ConstructorParams<Point> = {}) {
        this.x = x;
        this.y = y;
    }
}
