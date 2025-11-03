// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@generated/ftl";

import { ModuleName, setupI18n } from "@tslib/i18n";
import { optimumPixelSizeForCanvas } from "./canvas-scale";
import { Shape } from "./shapes";
import { Ellipse, extractShapesFromRenderedClozes, Polygon, Rectangle, Text } from "./shapes";
import { SHAPE_MASK_COLOR, TEXT_BACKGROUND_COLOR, TEXT_FONT_FAMILY, TEXT_PADDING } from "./tools/lib";
import type { Size } from "./types";

export type DrawShapesData = {
    activeShapes: Shape[];
    inactiveShapes: Shape[];
    highlightShapes: Shape[];
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
    const image = document.querySelector<HTMLImageElement>(
        "#image-occlusion-container img",
    );
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

async function setupImageOcclusionInner(setupOptions?: SetupImageOcclusionOptions): Promise<void> {
    const canvas = document.querySelector<HTMLCanvasElement>(
        "#image-occlusion-canvas",
    );
    if (canvas == null) {
        return;
    }

    const container = document.getElementById(
        "image-occlusion-container",
    ) as HTMLDivElement;
    const image = document.querySelector<HTMLImageElement>(
        "#image-occlusion-container img",
    );
    if (image == null) {
        await setupI18n({
            modules: [
                ModuleName.NOTETYPES,
            ],
        });
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
                toggleMasks(setupOptions);
            }
        });
        oneTimeSetupDone = true;
    }

    // setup button for toggle image occlusion
    const button = document.getElementById("toggle");
    if (button) {
        if (document.querySelector("[data-occludeinactive=\"1\"], [data-occludeinactive=\"2\"]")) {
            button.addEventListener("click", () => toggleMasks(setupOptions));
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
    allowedShapes?: Array<typeof Shape>,
): void {
    const context: CanvasRenderingContext2D = canvas.getContext("2d")!;
    const size = canvas;
    const filterByAllowedShapes = (el: Shape) =>
        (allowedShapes && allowedShapes.length > 0) ? allowedShapes.some(s => el instanceof s) : true;

    let activeShapes = extractShapesFromRenderedClozes(".cloze").filter(filterByAllowedShapes);
    let inactiveShapes = extractShapesFromRenderedClozes(".cloze-inactive").filter(filterByAllowedShapes);
    let highlightShapes = extractShapesFromRenderedClozes(".cloze-highlight").filter(filterByAllowedShapes);
    let properties = getShapeProperties();

    const processed = onWillDrawShapes?.({ activeShapes, inactiveShapes, highlightShapes, properties }, context);
    if (processed) {
        activeShapes = processed.activeShapes;
        inactiveShapes = processed.inactiveShapes;
        highlightShapes = processed.highlightShapes;
        properties = processed.properties;
    }

    // Determine occlusion mode from the first shape
    const occlusionMode = activeShapes[0]?.occlusionMode ?? inactiveShapes[0]?.occlusionMode ?? 0;

    // Mode 0 (HideOne): Draw active only (front), reveal answer with highlight (back)
    // Mode 1 (HideAll): Draw both active and inactive (front & back)
    // Mode 2 (HideAllButOne): Draw inactive only (front), draw nothing (back)

    // Check if we're on the back side (highlightShapes only exist on back)
    const isBackSide = highlightShapes.length > 0;

    // For mode 2 on the back side, draw nothing (show full unoccluded image)
    if (occlusionMode === 2 && isBackSide) {
        // Don't draw any shapes on the back for "Hide All But One" mode
    } else {
        // Normal drawing logic for all other cases
        if (occlusionMode !== 2) {
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
        }
        if (occlusionMode === 1 || occlusionMode === 2) {
            for (const shape of inactiveShapes) {
                drawShape({
                    context,
                    size,
                    shape,
                    fill: shape.fill !== SHAPE_MASK_COLOR ? shape.fill : properties.inActiveShapeColor,
                    stroke: properties.inActiveBorder.color,
                    strokeWidth: properties.inActiveBorder.width,
                });
            }
        }
        for (const shape of highlightShapes) {
            drawShape({
                context,
                size,
                shape,
                fill: properties.highlightShapeColor,
                stroke: properties.highlightShapeBorder.color,
                strokeWidth: properties.highlightShapeBorder.width,
            });
        }
    }

    onDidDrawShapes?.({
        activeShapes,
        inactiveShapes,
        highlightShapes,
        properties,
    }, context);
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
    const angle = ((shape.angle ?? 0) * Math.PI) / 180;
    if (shape instanceof Rectangle) {
        if (angle) {
            ctx.save();
            ctx.translate(shape.left, shape.top);
            ctx.rotate(angle);
            ctx.translate(-shape.left, -shape.top);
        }
        ctx.fillRect(shape.left, shape.top, shape.width, shape.height);
        // ctx stroke methods will draw a visible stroke, even if the width is 0
        if (strokeWidth) {
            ctx.strokeRect(shape.left, shape.top, shape.width, shape.height);
        }
        if (angle) { ctx.restore(); }
    } else if (shape instanceof Ellipse) {
        const adjustedLeft = shape.left + shape.rx;
        const adjustedTop = shape.top + shape.ry;
        if (angle) {
            ctx.save();
            ctx.translate(shape.left, shape.top);
            ctx.rotate(angle);
            ctx.translate(-shape.left, -shape.top);
        }
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
        if (angle) { ctx.restore(); }
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
        ctx.font = `${shape.fontSize}px ${TEXT_FONT_FAMILY}`;
        ctx.textBaseline = "top";
        ctx.scale(shape.scaleX, shape.scaleY);
        const lines = shape.text.split("\n");
        const baseMetrics = ctx.measureText("M");
        const fontHeight = baseMetrics.actualBoundingBoxAscent + baseMetrics.actualBoundingBoxDescent;
        const lineHeight = 1.5 * fontHeight;
        const linePositions: { text: string; x: number; y: number; width: number; height: number }[] = [];
        let maxWidth = 0;
        let totalHeight = 0;
        for (let i = 0; i < lines.length; i++) {
            const textMetrics = ctx.measureText(lines[i]);
            linePositions.push({
                text: lines[i],
                x: shape.left / shape.scaleX,
                y: shape.top / shape.scaleY + i * lineHeight,
                width: textMetrics.width,
                height: lineHeight,
            });
            if (textMetrics.width > maxWidth) {
                maxWidth = textMetrics.width;
            }
            totalHeight += lineHeight;
        }
        const left = shape.left / shape.scaleX;
        const top = shape.top / shape.scaleY;
        if (angle) {
            ctx.translate(left, top);
            ctx.rotate(angle);
            ctx.translate(-left, -top);
        }
        ctx.fillStyle = TEXT_BACKGROUND_COLOR;
        ctx.fillRect(
            left,
            top,
            maxWidth + TEXT_PADDING,
            totalHeight + TEXT_PADDING,
        );
        ctx.fillStyle = shape.fill ?? "#000";
        for (const line of linePositions) {
            ctx.fillText(line.text, line.x, line.y);
        }
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
    highlightShapeColor: string;
    activeBorder: { width: number; color: string };
    inActiveBorder: { width: number; color: string };
    highlightShapeBorder: { width: number; color: string };
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
        const highlightShapeColor = computedStyle.getPropertyValue(
            "--highlight-shape-color",
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
        // highlight shape border
        const highlightShapeBorder = computedStyle.getPropertyValue(
            "--highlight-shape-border",
        );
        const highlightBorder = highlightShapeBorder.split(" ").filter((x) => x);
        const highlightShapeBorderWidth = parseFloat(highlightBorder[0]);
        const highlightShapeBorderColor = highlightBorder[1];

        return {
            activeShapeColor: activeShapeColor ? activeShapeColor : "#ff8e8e",
            inActiveShapeColor: inActiveShapeColor
                ? inActiveShapeColor
                : SHAPE_MASK_COLOR,
            highlightShapeColor: highlightShapeColor
                ? highlightShapeColor
                : "#ff8e8e00",
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
            highlightShapeBorder: {
                width: !isNaN(highlightShapeBorderWidth) ? highlightShapeBorderWidth : 1,
                color: highlightShapeBorderColor
                    ? highlightShapeBorderColor
                    : "#ff8e8e",
            },
        };
    } catch {
        // return default values
        return {
            activeShapeColor: "#ff8e8e",
            inActiveShapeColor: "#ffeba2",
            highlightShapeColor: "#ff8e8e00",
            activeBorder: {
                width: 1,
                color: "#212121",
            },
            inActiveBorder: {
                width: 1,
                color: "#212121",
            },
            highlightShapeBorder: {
                width: 1,
                color: "#ff8e8e",
            },
        };
    }
}

let hide = false;
const toggleMasks = (setupOptions?: SetupImageOcclusionOptions): void => {
    const canvas = document.getElementById("image-occlusion-canvas") as HTMLCanvasElement;
    const context = canvas.getContext("2d")!;

    hide = !hide;
    context.clearRect(0, 0, canvas.width, canvas.height);
    if (hide) {
        drawShapes(canvas, setupOptions?.onWillDrawShapes, setupOptions?.onDidDrawShapes, [Text]);
        return;
    }
    drawShapes(canvas, setupOptions?.onWillDrawShapes, setupOptions?.onDidDrawShapes);
};
