// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import type { ConstructorParams, Size } from "../types";
import type { ShapeDataForCloze } from "./base";
import { Shape } from "./base";
import { floatToDisplay } from "./lib";
import { xFromNormalized, xToNormalized, yFromNormalized, yToNormalized } from "./position";

export class Polygon extends Shape {
    points: Point[];

    constructor({ points = [], ...rest }: ConstructorParams<Polygon> = {}) {
        super(rest);
        this.points = points;
        this.id = "polygon-" + new Date().getTime();
    }

    toDataForCloze(): PolygonDataForCloze {
        return {
            ...super.toDataForCloze(),
            points: this.points.map(({ x, y }) => `${floatToDisplay(x)},${floatToDisplay(y)}`).join(" "),
        };
    }

    toFabric(size: Size): fabric.Polygon {
        const absolute = this.toAbsolute(size);
        // @ts-expect-error absolute is our own object not a fabric.Polygon
        return new fabric.Polygon(absolute.points, absolute);
    }

    toNormal(size: Size): Polygon {
        const points: Point[] = [];
        this.points.forEach((p) => {
            points.push({
                x: xToNormalized(size, p.x),
                y: yToNormalized(size, p.y),
            });
        });
        return new Polygon({
            ...this,
            ...super.normalPosition(size),
            points,
        });
    }

    toAbsolute(size: Size): Polygon {
        const points: Point[] = [];
        this.points.forEach((p) => {
            points.push({
                x: xFromNormalized(size, p.x),
                y: yFromNormalized(size, p.y),
            });
        });
        return new Polygon({
            ...this,
            ...super.absolutePosition(size),
            points,
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
