// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";

import { optimumPixelSizeForCanvas } from "./canvas-scale";
import { Shape } from "./shapes";
import { Ellipse, extractShapesFromRenderedClozes, Polygon, Rectangle, Text } from "./shapes";
import { TEXT_BACKGROUND_COLOR, TEXT_FONT_FAMILY, TEXT_PADDING } from "./tools/lib";
import type { Size } from "./types";

export type DrawShapesData = {
    activeShapes: Shape[];
    inactiveShapes: Shape[];
    properties: ShapeProperties;
};

export type DrawShapesFilter = (
    data: DrawShapesData,
    context: CanvasRenderingContext2D,
) => DrawShapesData | void;

export type DrawShapesCallback = (
    data: DrawShapesData,
    context: CanvasRenderingContext2D,
) => void;

export const imageOcclusionAPI = {
    setup: setupImageOcclusion,
    drawShape,
    Ellipse,
    Polygon,
    Rectangle,
    Shape,
    Text,
};

interface SetupImageOcclusionOptions {
    onWillDrawShapes?: DrawShapesFilter;
    onDidDrawShapes?: DrawShapesCallback;
}

async function setupImageOcclusion(setupOptions?: SetupImageOcclusionOptions): Promise<void> {
    await waitForImage();
    window.addEventListener("load", () => {
        window.addEventListener("resize", () => setupImageOcclusion(setupOptions));
    });
    window.requestAnimationFrame(() => setupImageOcclusionInner(setupOptions));
}

/** We must make sure the image has loaded before we can access its dimensions.
 * This can happen if not preloading, or if preloading takes too long. */
async function waitForImage(): Promise<void> {
    const image = document.querySelector(
        "#image-occlusion-container img",
    ) as HTMLImageElement | null;
    if (!image) {
        // error will be handled later
        return;
    }

    if (image.complete) {
        return;
    }

    // Wait for the image to load
    await new Promise<void>(resolve => {
        image.addEventListener("load", () => {
            resolve();
        });
        image.addEventListener("error", () => {
            resolve();
        });
    });
}

/**
 * Calculate the size of the container that will fit in the viewport while having
 * the same aspect ratio as the image. This is a workaround for Qt5 WebEngine not
 * supporting the `aspect-ratio` CSS property.
 */
function calculateContainerSize(
    container: HTMLDivElement,
    img: HTMLImageElement,
): { width: number; height: number } {
    const compStyle = getComputedStyle(container);

    const compMaxWidth = parseFloat(compStyle.getPropertyValue("max-width"));
    const vw = container.parentElement!.clientWidth;
    // respect 'max-width' if it is set narrower than the viewport
    const maxWidth = Number.isNaN(compMaxWidth) || compMaxWidth > vw ? vw : compMaxWidth;

    // see ./review.scss
    const defaultMaxHeight = document.documentElement.clientHeight * 0.95 - 40;
    const compMaxHeight = parseFloat(compStyle.getPropertyValue("max-height"));
    let maxHeight: number;
    // If 'max-height' is set to 'unset' or 'initial' and the image is taller than
    // the default max height, the container height is up to the image height.
    if (Number.isNaN(compMaxHeight)) {
        maxHeight = Math.max(img.naturalHeight, defaultMaxHeight);
    } else if (compMaxHeight < defaultMaxHeight) {
        maxHeight = compMaxHeight;
    } else {
        maxHeight = Math.max(defaultMaxHeight, Math.min(img.naturalHeight, compMaxHeight));
    }

    const ratio = Math.min(
        maxWidth / img.naturalWidth,
        maxHeight / img.naturalHeight,
    );
    return { width: img.naturalWidth * ratio, height: img.naturalHeight * ratio };
}

let oneTimeSetupDone = false;

function setupImageOcclusionInner(setupOptions?: SetupImageOcclusionOptions): void {
    const canvas = document.querySelector(
        "#image-occlusion-canvas",
    ) as HTMLCanvasElement | null;
    if (canvas == null) {
        return;
    }

    const container = document.getElementById(
        "image-occlusion-container",
    ) as HTMLDivElement;
    const image = document.querySelector(
        "#image-occlusion-container img",
    ) as HTMLImageElement;
    if (image == null) {
        container.innerText = tr.notetypeErrorNoImageToShow();
        return;
    }

    // Enforce aspect ratio of image
    if (CSS.supports("aspect-ratio: 1")) {
        container.style.aspectRatio = `${image.naturalWidth / image.naturalHeight}`;
    } else {
        const containerSize = calculateContainerSize(container, image);
        container.style.width = `${containerSize.width}px`;
        container.style.height = `${containerSize.height}px`;
    }

    const size = optimumPixelSizeForCanvas(
        { width: image.naturalWidth, height: image.naturalHeight },
        { width: canvas.clientWidth, height: canvas.clientHeight },
    );
    canvas.width = size.width;
    canvas.height = size.height;

    if (!oneTimeSetupDone) {
        window.addEventListener("keydown", (event) => {
            const button = document.getElementById("toggle");
            if (button && button.style.display !== "none" && event.key === "M") {
                toggleMasks();
            }
        });
        oneTimeSetupDone = true;
    }

    // setup button for toggle image occlusion
    const button = document.getElementById("toggle");
    if (button) {
        if (document.querySelector("[data-occludeinactive=\"1\"]")) {
            button.addEventListener("click", toggleMasks);
        } else {
            button.style.display = "none";
        }
    }

    drawShapes(canvas, setupOptions?.onWillDrawShapes, setupOptions?.onDidDrawShapes);
}

function drawShapes(
    canvas: HTMLCanvasElement,
    onWillDrawShapes?: DrawShapesFilter,
    onDidDrawShapes?: DrawShapesCallback,
): void {
    const context: CanvasRenderingContext2D = canvas.getContext("2d")!;
    const size = canvas;

    let activeShapes = extractShapesFromRenderedClozes(".cloze");
    let inactiveShapes = extractShapesFromRenderedClozes(".cloze-inactive");
    let properties = getShapeProperties();

    const processed = onWillDrawShapes?.({ activeShapes, inactiveShapes, properties }, context);
    if (processed) {
        activeShapes = processed.activeShapes;
        inactiveShapes = processed.inactiveShapes;
        properties = processed.properties;
    }

    for (const shape of activeShapes) {
        drawShape({
            context,
            size,
            shape,
            fill: properties.activeShapeColor,
            stroke: properties.activeBorder.color,
            strokeWidth: properties.activeBorder.width,
        });
    }
    for (const shape of inactiveShapes.filter((s) => s.occludeInactive)) {
        drawShape({
            context,
            size,
            shape,
            fill: properties.inActiveShapeColor,
            stroke: properties.inActiveBorder.color,
            strokeWidth: properties.inActiveBorder.width,
        });
    }

    onDidDrawShapes?.({ activeShapes, inactiveShapes, properties }, context);
}

interface DrawShapeParameters {
    context: CanvasRenderingContext2D;
    size: Size;
    shape: Shape;
    fill: string;
    stroke: string;
    strokeWidth: number;
}

function drawShape({
    context: ctx,
    size,
    shape,
    fill,
    stroke,
    strokeWidth,
}: DrawShapeParameters): void {
    shape = shape.toAbsolute(size);

    ctx.fillStyle = fill;
    ctx.strokeStyle = stroke;
    ctx.lineWidth = strokeWidth;
    if (shape instanceof Rectangle) {
        ctx.fillRect(shape.left, shape.top, shape.width, shape.height);
        // ctx stroke methods will draw a visible stroke, even if the width is 0
        if (strokeWidth) {
            ctx.strokeRect(shape.left, shape.top, shape.width, shape.height);
        }
    } else if (shape instanceof Ellipse) {
        const adjustedLeft = shape.left + shape.rx;
        const adjustedTop = shape.top + shape.ry;
        ctx.beginPath();
        ctx.ellipse(
            adjustedLeft,
            adjustedTop,
            shape.rx,
            shape.ry,
            0,
            0,
            Math.PI * 2,
            false,
        );
        ctx.closePath();
        ctx.fill();
        if (strokeWidth) {
            ctx.stroke();
        }
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
        if (strokeWidth) {
            ctx.stroke();
        }
        ctx.restore();
    } else if (shape instanceof Text) {
        ctx.save();
        ctx.font = `40px ${TEXT_FONT_FAMILY}`;
        ctx.textBaseline = "top";
        ctx.scale(shape.scaleX, shape.scaleY);
        const textMetrics = ctx.measureText(shape.text);
        ctx.fillStyle = TEXT_BACKGROUND_COLOR;
        ctx.fillRect(
            shape.left / shape.scaleX,
            shape.top / shape.scaleY,
            textMetrics.width + TEXT_PADDING,
            textMetrics.actualBoundingBoxDescent + TEXT_PADDING,
        );
        ctx.fillStyle = "#000";
        ctx.fillText(
            shape.text,
            shape.left / shape.scaleX,
            shape.top / shape.scaleY,
        );
        ctx.restore();
    }
}

function getPolygonOffset(polygon: Polygon): { x: number; y: number } {
    const topLeft = topLeftOfPoints(polygon.points);
    return { x: polygon.left - topLeft.x, y: polygon.top - topLeft.y };
}

function topLeftOfPoints(points: { x: number; y: number }[]): {
    x: number;
    y: number;
} {
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

export type ShapeProperties = {
    activeShapeColor: string;
    inActiveShapeColor: string;
    activeBorder: { width: number; color: string };
    inActiveBorder: { width: number; color: string };
};
function getShapeProperties(): ShapeProperties {
    const canvas = document.getElementById("image-occlusion-canvas");
    const computedStyle = window.getComputedStyle(canvas!);
    // it may throw error if the css variable is not defined
    try {
        // shape color
        const activeShapeColor = computedStyle.getPropertyValue(
            "--active-shape-color",
        );
        const inActiveShapeColor = computedStyle.getPropertyValue(
            "--inactive-shape-color",
        );
        // inactive shape border
        const inActiveShapeBorder = computedStyle.getPropertyValue(
            "--inactive-shape-border",
        );
        const inActiveBorder = inActiveShapeBorder.split(" ").filter((x) => x);
        const inActiveShapeBorderWidth = parseFloat(inActiveBorder[0]);
        const inActiveShapeBorderColor = inActiveBorder[1];
        // active shape border
        const activeShapeBorder = computedStyle.getPropertyValue(
            "--active-shape-border",
        );
        const activeBorder = activeShapeBorder.split(" ").filter((x) => x);
        const activeShapeBorderWidth = parseFloat(activeBorder[0]);
        const activeShapeBorderColor = activeBorder[1];

        return {
            activeShapeColor: activeShapeColor ? activeShapeColor : "#ff8e8e",
            inActiveShapeColor: inActiveShapeColor
                ? inActiveShapeColor
                : "#ffeba2",
            activeBorder: {
                width: !isNaN(activeShapeBorderWidth) ? activeShapeBorderWidth : 1,
                color: activeShapeBorderColor
                    ? activeShapeBorderColor
                    : "#212121",
            },
            inActiveBorder: {
                width: !isNaN(inActiveShapeBorderWidth) ? inActiveShapeBorderWidth : 1,
                color: inActiveShapeBorderColor
                    ? inActiveShapeBorderColor
                    : "#212121",
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
    const canvas = document.getElementById(
        "image-occlusion-canvas",
    ) as HTMLCanvasElement;
    const display = canvas.style.display;
    if (display === "none") {
        canvas.style.display = "unset";
    } else {
        canvas.style.display = "none";
    }
};
