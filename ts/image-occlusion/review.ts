// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";

import { optimumPixelSizeForCanvas } from "./canvas-scale";
import type { Shape } from "./shapes/base";
import { Ellipse } from "./shapes/ellipse";
import { extractShapesFromRenderedClozes } from "./shapes/from-cloze";
import { Polygon } from "./shapes/polygon";
import { Rectangle } from "./shapes/rectangle";
import type { Size } from "./types";

export function setupImageCloze(): void {
    window.addEventListener("load", () => {
        window.addEventListener("resize", setupImageCloze);
    });
    window.requestAnimationFrame(setupImageClozeInner);
}

function setupImageClozeInner(): void {
    const canvas = document.querySelector("#image-occlusion-canvas") as HTMLCanvasElement | null;
    if (canvas == null) {
        return;
    }

    const ctx: CanvasRenderingContext2D = canvas.getContext("2d")!;
    const container = document.getElementById("image-occlusion-container") as HTMLDivElement;
    const image = document.querySelector("#image-occlusion-container img") as HTMLImageElement;
    if (image == null) {
        container.innerText = tr.notetypeErrorNoImageToShow();
        return;
    }

    // Enforce aspect ratio of image
    container.style.aspectRatio = `${image.naturalWidth / image.naturalHeight}`;

    const size = optimumPixelSizeForCanvas(
        { width: image.naturalWidth, height: image.naturalHeight },
        { width: canvas.clientWidth, height: canvas.clientHeight },
    );
    canvas.width = size.width;
    canvas.height = size.height;

    // setup button for toggle image occlusion
    const button = document.getElementById("toggle");
    if (button) {
        button.addEventListener("click", toggleMasks);
    }

    drawShapes(canvas, ctx);
}

function drawShapes(canvas: HTMLCanvasElement, ctx: CanvasRenderingContext2D): void {
    const shapeProperty = getShapeProperty();
    const size = canvas;
    for (const active of extractShapesFromRenderedClozes(".cloze")) {
        const fill = shapeProperty.activeShapeColor;
        drawShape(ctx, size, active, fill, shapeProperty.activeBorder);
    }
    for (const inactive of extractShapesFromRenderedClozes(".cloze-inactive")) {
        const fill = shapeProperty.inActiveShapeColor;
        if (inactive.occludeInactive) {
            drawShape(ctx, size, inactive, fill, shapeProperty.inActiveBorder);
        }
    }
}

function drawShape(
    ctx: CanvasRenderingContext2D,
    size: Size,
    shape: Shape,
    color: string,
    border: { width: number; color: string },
): void {
    shape.makeAbsolute(size);
    ctx.fillStyle = color;
    ctx.strokeStyle = border.color;
    ctx.lineWidth = border.width;
    if (shape instanceof Rectangle) {
        ctx.fillRect(shape.left, shape.top, shape.width, shape.height);
        ctx.strokeRect(shape.left, shape.top, shape.width, shape.height);
    } else if (shape instanceof Ellipse) {
        const adjustedLeft = shape.left + shape.rx;
        const adjustedTop = shape.top + shape.ry;
        ctx.beginPath();
        ctx.ellipse(adjustedLeft, adjustedTop, shape.rx, shape.ry, 0, 0, Math.PI * 2, false);
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
    } else if (shape instanceof Polygon) {
        const offset = getPolygonOffset(shape);
        ctx.save();
        ctx.translate(offset.x, offset.y);
        ctx.beginPath();
        ctx.moveTo(shape.points[0].x, shape.points[0].y);
        for (let i = 0; i < shape.points.length; i++) {
            ctx.lineTo(shape.points[i].x, shape.points[i].y);
        }
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
        ctx.restore();
    }
}

function getPolygonOffset(polygon: Polygon): { x: number; y: number } {
    const topLeft = topLeftOfPoints(polygon.points);
    return { x: polygon.left - topLeft.x, y: polygon.top - topLeft.y };
}

function topLeftOfPoints(points: { x: number; y: number }[]): { x: number; y: number } {
    let top = points[0].y;
    let left = points[0].x;
    for (const point of points) {
        if (point.y < top) {
            top = point.y;
        }
        if (point.x < left) {
            left = point.x;
        }
    }
    return { x: left, y: top };
}

function getShapeProperty(): {
    activeShapeColor: string;
    inActiveShapeColor: string;
    activeBorder: { width: number; color: string };
    inActiveBorder: { width: number; color: string };
} {
    const canvas = document.getElementById("image-occlusion-canvas");
    const computedStyle = window.getComputedStyle(canvas!);
    // it may throw error if the css variable is not defined
    try {
        // shape color
        const activeShapeColor = computedStyle.getPropertyValue("--active-shape-color");
        const inActiveShapeColor = computedStyle.getPropertyValue("--inactive-shape-color");
        // inactive shape border
        const inActiveShapeBorder = computedStyle.getPropertyValue("--inactive-shape-border");
        const inActiveBorder = inActiveShapeBorder.split(" ").filter((x) => x);
        const inActiveShapeBorderWidth = parseFloat(inActiveBorder[0]);
        const inActiveShapeBorderColor = inActiveBorder[1];
        // active shape border
        const activeShapeBorder = computedStyle.getPropertyValue("--active-shape-border");
        const activeBorder = activeShapeBorder.split(" ").filter((x) => x);
        const activeShapeBorderWidth = parseFloat(activeBorder[0]);
        const activeShapeBorderColor = activeBorder[1];

        return {
            activeShapeColor: activeShapeColor ? activeShapeColor : "#ff8e8e",
            inActiveShapeColor: inActiveShapeColor ? inActiveShapeColor : "#ffeba2",
            activeBorder: {
                width: activeShapeBorderWidth ? activeShapeBorderWidth : 1,
                color: activeShapeBorderColor ? activeShapeBorderColor : "#212121",
            },
            inActiveBorder: {
                width: inActiveShapeBorderWidth ? inActiveShapeBorderWidth : 1,
                color: inActiveShapeBorderColor ? inActiveShapeBorderColor : "#212121",
            },
        };
    } catch {
        // return default values
        return {
            activeShapeColor: "#ff8e8e",
            inActiveShapeColor: "#ffeba2",
            activeBorder: {
                width: 1,
                color: "#212121",
            },
            inActiveBorder: {
                width: 1,
                color: "#212121",
            },
        };
    }
}

const toggleMasks = (): void => {
    const canvas = document.getElementById("image-occlusion-canvas") as HTMLCanvasElement;
    const display = canvas.style.display;
    if (display === "none") {
        canvas.style.display = "unset";
    } else {
        canvas.style.display = "none";
    }
};
