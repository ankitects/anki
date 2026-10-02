// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";
import { afterEach, describe, expect, test, vi } from "vitest";

import { addShape, addShapeGroup } from "../tools/from-shapes";
import type { Size } from "../types";
import type { Shape } from "./base";
import { Ellipse } from "./ellipse";
import { Polygon } from "./polygon";
import { Rectangle } from "./rectangle";
import { exportShapesToClozeDeletions } from "./to-cloze";

afterEach(() => vi.unstubAllGlobals());

function setUpCanvas(size: Size) {
    const boundingBox = new fabric.Rect({ ...size, fill: "transparent", strokeWidth: 0 });
    const objects: fabric.Object[] = [boundingBox];
    vi.stubGlobal("canvas", {
        add: (object: fabric.Object) => objects.push(object),
        getObjects: () => objects,
        getActiveObject: () => undefined,
    });
    return boundingBox;
}

const rectangle = new Rectangle({
    ordinal: 1,
    left: 0.3734,
    top: 0.2508,
    width: 0.3858,
    height: 0.2429,
});
const ellipse = new Ellipse({
    ordinal: 1,
    left: 0.1734,
    top: 0.1508,
    rx: 0.1929,
    ry: 0.1214,
});
const polygon = new Polygon({
    ordinal: 1,
    left: 0.3734,
    top: 0.2508,
    points: [{ x: 0, y: 0 }, { x: 0.3858, y: 0.0109 }, { x: 0.0093, y: 0.2429 }],
});
const cases: [string, Shape, string][] = [
    ["rectangle", rectangle, "rect:left=.3734:top=.2508:width=.3858:height=.2429"],
    ["ellipse", ellipse, "ellipse:left=.1734:top=.1508:rx=.1929:ry=.1214"],
    ["polygon", polygon, "polygon:left=.3734:top=.2508:points=.0000,.0000 .3858,.0109 .0093,.2429"],
];

describe.each([{ width: 1000, height: 1000 }, { width: 1379, height: 853 }])(
    "saving masks on a $width x $height image",
    (size) => {
        test.each(cases)("preserves untouched %s coordinates", (_name, shape, cloze) => {
            const boundingBox = setUpCanvas(size);
            addShape(globalThis.canvas, boundingBox, shape);

            expect(exportShapesToClozeDeletions(false)).toEqual({
                clozes: `{{c1::image-occlusion:${cloze}}}<br>`,
                noteCount: 1,
            });
        });

        test("preserves untouched grouped mask coordinates", () => {
            const boundingBox = setUpCanvas(size);
            addShapeGroup(globalThis.canvas, boundingBox, [rectangle, ellipse]);

            expect(exportShapesToClozeDeletions(false)).toEqual({
                clozes: `{{c1::image-occlusion:${cases[0][2]}}}<br>`
                    + `{{c1::image-occlusion:${cases[1][2]}}}<br>`,
                noteCount: 1,
            });
        });
    },
);
