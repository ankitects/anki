// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
*/

import type { Shape, ShapeOrShapes } from "./base";
import { Ellipse } from "./ellipse";
import { Point, Polygon } from "./polygon";
import { Rectangle } from "./rectangle";

/** Given a cloze field with text like the following, extract the shapes from it:
 * {{c1::image-occlusion:rect:left=10.0:top=20:width=30:height=10:fill=#ffe34d}}
 */
export function extractShapesFromClozedField(clozeStr: string): ShapeOrShapes[] {
    const regex = /{{(.*?)}}/g;
    const clozeStrList: string[] = [];
    let match: string[] | null;

    while ((match = regex.exec(clozeStr)) !== null) {
        clozeStrList.push(match[1]);
    }

    const clozeList = {};
    for (const str of clozeStrList) {
        const [prefix, value] = str.split("::image-occlusion:");
        if (!clozeList[prefix]) {
            clozeList[prefix] = [];
        }
        clozeList[prefix].push(value);
    }

    const output: ShapeOrShapes[] = [];

    for (const index in clozeList) {
        if (clozeList[index].length > 1) {
            const group: Shape[] = [];
            clozeList[index].forEach((cloze) => {
                let shape: Shape | null = null;
                if ((shape = extractShapeFromClozeText(cloze))) {
                    group.push(shape);
                }
            });
            output.push(group);
        } else {
            let shape: Shape | null = null;
            if ((shape = extractShapeFromClozeText(clozeList[index][0]))) {
                output.push(shape);
            }
        }
    }
    return output;
}

function extractShapeFromClozeText(text: string): Shape | null {
    const [type, props] = extractTypeAndPropsFromClozeText(text);
    if (!type) {
        return null;
    }
    return buildShape(type, props);
}

function extractTypeAndPropsFromClozeText(text: string): [ShapeType | null, Record<string, any>] {
    const parts = text.split(":");
    const type = parts[0];
    if (type !== "rect" && type !== "ellipse" && type !== "polygon") {
        return [null, {}];
    }
    const props = {};
    for (let i = 1; i < parts.length; i++) {
        const [key, value] = parts[i].split("=");
        props[key] = value;
    }
    return [type, props];
}

/** Locate all cloze divs in the review screen for the given selector, and convert them into BaseShapes.
 */
export function extractShapesFromRenderedClozes(selector: string): Shape[] {
    return Array.from(document.querySelectorAll(selector)).flatMap((cloze) => {
        if (cloze instanceof HTMLDivElement) {
            return extractShapeFromRenderedCloze(cloze) ?? [];
        } else {
            return [];
        }
    });
}

function extractShapeFromRenderedCloze(cloze: HTMLDivElement): Shape | null {
    const type = cloze.dataset.shape!;
    if (type !== "rect" && type !== "ellipse" && type !== "polygon") {
        return null;
    }
    const props = {
        occludeInactive: cloze.dataset.occludeinactive === "1",
        left: cloze.dataset.left,
        top: cloze.dataset.top,
        width: cloze.dataset.width,
        height: cloze.dataset.height,
        rx: cloze.dataset.rx,
        ry: cloze.dataset.ry,
        points: cloze.dataset.points,
    };
    return buildShape(type, props);
}

type ShapeType = "rect" | "ellipse" | "polygon";

function buildShape(type: ShapeType, props: Record<string, any>): Shape {
    props.left = parseFloat(props.left);
    props.top = parseFloat(props.top);
    switch (type) {
        case "rect": {
            return new Rectangle({ ...props, width: parseFloat(props.width), height: parseFloat(props.height) });
        }
        case "ellipse": {
            return new Ellipse({
                ...props,
                rx: parseFloat(props.rx),
                ry: parseFloat(props.ry),
            });
        }
        case "polygon": {
            if (props.points !== "") {
                props.points = props.points.split(" ").map((point) => {
                    const [x, y] = point.split(",");
                    return new Point({ x, y });
                });
            } else {
                props.points = [new Point({ x: 0, y: 0 })];
            }
            return new Polygon(props);
        }
    }
}
