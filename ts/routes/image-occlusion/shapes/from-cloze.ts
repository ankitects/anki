// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
*/

import type { GetImageOcclusionNoteResponse_ImageOcclusion } from "@generated/anki/image_occlusion_pb";

import type { Shape, ShapeOrShapes } from "./base";
import { Ellipse } from "./ellipse";
import { storedToAngle } from "./lib";
import { Point, Polygon } from "./polygon";
import { Rectangle } from "./rectangle";
import { Text } from "./text";

export function extractShapesFromClozedField(
    occlusions: GetImageOcclusionNoteResponse_ImageOcclusion[],
): ShapeOrShapes[] {
    const output: ShapeOrShapes[] = [];
    for (const occlusion of occlusions) {
        const group: Shape[] = [];
        for (const shape of occlusion.shapes) {
            if (isValidType(shape.shape)) {
                const props: Record<string, any> = Object.fromEntries(
                    shape.properties.map(prop => [prop.name, prop.value]),
                );
                props.ordinal = occlusion.ordinal;
                group.push(buildShape(shape.shape, props));
            }
        }
        if (occlusion.ordinal === 0) {
            output.push(...group);
        } else if (group.length > 1) {
            output.push(group);
        } else {
            output.push(group[0]);
        }
    }

    return output;
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
    if (
        type !== "rect"
        && type !== "ellipse"
        && type !== "polygon"
        && type !== "text"
    ) {
        return null;
    }
    const props = {
        occludeInactive: cloze.dataset.occludeinactive === "1",
        ordinal: parseInt(cloze.dataset.ordinal!),
        left: cloze.dataset.left,
        top: cloze.dataset.top,
        width: cloze.dataset.width,
        height: cloze.dataset.height,
        rx: cloze.dataset.rx,
        ry: cloze.dataset.ry,
        points: cloze.dataset.points,
        text: cloze.dataset.text,
        scale: cloze.dataset.scale,
        fs: cloze.dataset.fontSize,
        angle: cloze.dataset.angle,
    };
    return buildShape(type, props);
}

type ShapeType = "rect" | "ellipse" | "polygon" | "text";

function isValidType(type: string): type is ShapeType {
    return ["rect", "ellipse", "polygon", "text"].includes(type);
}

function buildShape(type: ShapeType, props: Record<string, any>): Shape {
    props.left = parseFloat(
        Number.isNaN(Number(props.left)) ? ".0000" : props.left,
    );
    props.top = parseFloat(
        Number.isNaN(Number(props.top)) ? ".0000" : props.top,
    );
    props.angle = storedToAngle(props.angle) ?? 0;
    switch (type) {
        case "rect": {
            return new Rectangle({
                ...props,
                width: parseFloat(props.width),
                height: parseFloat(props.height),
            });
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
        case "text": {
            const textProps: Record<string, any> = {
                ...props,
                scaleX: parseFloat(props.scale),
                scaleY: parseFloat(props.scale),
            };
            if (props.fs) {
                textProps.fontSize = parseFloat(props.fs);
            }
            return new Text(textProps);
        }
    }
}
