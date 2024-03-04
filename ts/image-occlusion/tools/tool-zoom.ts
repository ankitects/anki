// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// https://codepen.io/amsunny/pen/XWGLxye
// canvas.viewportTransform = [ scaleX, skewX, skewY, scaleY, translateX, translateY ]

import Hammer from "hammerjs";

let isDragging = false;

const minScale = 0.2;
const maxScale = 5;
let zoomScale = 1;
let currentScale = 1;

export const enableZoom = (canvas) => {
    canvas.on("mouse:wheel", onMouseWheel);
    canvas.on("mouse:down", onMouseDown);
    canvas.on("mouse:move", onMouseMove);
    canvas.on("mouse:up", onMouseUp);

    const hammer = new Hammer(canvas.upperCanvasEl);
    hammer.get("pinch").set({ enable: true });
    hammer.on("pinch pinchmove", ev => {
        currentScale = Math.min(Math.max(minScale, ev.scale * zoomScale), maxScale);
    });
    hammer.on("pinchend pinchcancel", () => {
        zoomScale = currentScale;
    });
};

export const disableZoom = (canvas) => {
    canvas.off("mouse:wheel", onMouseWheel);
    canvas.off("mouse:down", onMouseDown);
    canvas.off("mouse:move", onMouseMove);
    canvas.off("mouse:up", onMouseUp);

    const hammer = new Hammer(canvas.upperCanvasEl);
    hammer.get("pinch").set({ enable: false });
    hammer.off("pinch pinchmove pinchend pinchcancel");
};

export const zoomIn = (canvas): void => {
    let zoom = canvas.getZoom();
    zoom = Math.min(maxScale, zoom * 1.1);
    canvas.zoomToPoint({ x: canvas.width / 2, y: canvas.height / 2 }, zoom);
};

export const zoomOut = (canvas): void => {
    let zoom = canvas.getZoom();
    zoom = Math.max(minScale, zoom / 1.1);
    canvas.zoomToPoint({ x: canvas.width / 2, y: canvas.height / 2 }, zoom / 1.1);
};

export const zoomReset = (canvas): void => {
    canvas.zoomToPoint({ x: canvas.width / 2, y: canvas.height / 2 }, 1);
    fitCanvasVptScale(canvas);
    constrainBoundsAroundBgImage(canvas);
};

export const onResize = (canvas) => {
    setCanvasSize(canvas);
    scaleCanvasBgImage(canvas);

    const canvasBgImage = canvas.backgroundImage;
    if (canvasBgImage) {
        updateCanvasFocusPoint(canvas, canvasBgImage);
    }

    constrainBoundsAroundBgImage(canvas);
    fitCanvasVptScale(canvas);
};

const onMouseWheel = (opt) => {
    const canvas = globalThis.canvas;
    const delta = opt.e.deltaY;
    let zoom = canvas.getZoom();
    zoom *= 0.999 ** delta;
    zoom = Math.max(minScale, Math.min(zoom, maxScale));
    canvas.zoomToPoint({ x: opt.pointer.x, y: opt.pointer.y }, zoom);
    opt.e.preventDefault();
    opt.e.stopPropagation();
};

const onMouseDown = (opt) => {
    isDragging = true;
    const canvas = globalThis.canvas;
    canvas.discardActiveObject();
    const { e } = opt;
    const clientX = e.type === "touchstart" ? e.touches[0].clientX : e.clientX;
    const clientY = e.type === "touchstart" ? e.touches[0].clientY : e.clientY;
    canvas.lastPosX = clientX;
    canvas.lastPosY = clientY;
    canvas.requestRenderAll();
};

const onMouseMove = (opt) => {
    const canvas = globalThis.canvas;
    if (isDragging) {
        canvas.discardActiveObject();
        canvas.defaultCursor = "grabbing";
        const { e } = opt;
        if (!canvas.viewportTransform) {
            return;
        }

        if ((e.type === "touchmove") && (e.touches.length > 1)) {
            canvas.zoomToPoint({ x: canvas.width / 2, y: canvas.height / 2 }, currentScale);
            return;
        }

        const clientX = e.type === "touchmove" ? e.touches[0].clientX : e.clientX;
        const clientY = e.type === "touchmove" ? e.touches[0].clientY : e.clientY;
        const vpt = canvas.viewportTransform;
        vpt[4] += clientX - canvas.lastPosX;
        vpt[5] += clientY - canvas.lastPosY;
        canvas.lastPosX = clientX;
        canvas.lastPosY = clientY;
        canvas.requestRenderAll();
    }
};

const onMouseUp = () => {
    isDragging = false;
    const canvas = globalThis.canvas;
    canvas.setViewportTransform(canvas.viewportTransform);
    canvas.defaultCursor = "default";
    canvas.requestRenderAll();
};

const constrainBoundsAroundBgImage = (canvas) => {
    const canvasWidth = canvas.getWidth();
    const canvasHeight = canvas.getHeight();
    const vpt = canvas.viewportTransform;
    const canvasBgImage = canvas.backgroundImage;

    if (!canvasBgImage) {
        return;
    }

    const {
        bgImageWidth,
        bgImageHeight,
    } = getCanvasBgImageCalculatedSize(canvas, canvasBgImage);

    const minX = Math.min(0, canvasWidth - bgImageWidth);
    const minY = Math.min(0, canvasHeight - bgImageHeight);

    vpt[4] = Math.max(minX, Math.min(0, vpt[4]));
    vpt[5] = Math.max(minY, Math.min(0, vpt[5]));

    updateCanvasFocusPoint(canvas, canvasBgImage);
};

export const setCanvasSize = (canvas) => {
    canvas.setHeight(window.innerHeight - 76);
    canvas.setWidth(window.innerWidth - 39);
};

const scaleCanvasBgImage = (canvas) => {
    const canvasBgImage = canvas.backgroundImage;
    const boundingBox = globalThis.boundingBox;
    if (!canvasBgImage) {
        return;
    }

    canvasBgImage.scaleToWidth(canvas.width * canvas.getZoom());
    canvasBgImage.scaleToHeight(canvas.height * canvas.getZoom());

    const ratioW = boundingBox.width / canvasBgImage.width;
    const ratioH = boundingBox.height / canvasBgImage.height;

    if (ratioW > ratioH) {
        canvasBgImage.scaleX = ratioW;
        canvasBgImage.scaleY = ratioW;
    } else {
        canvasBgImage.scaleX = ratioH;
        canvasBgImage.scaleY = ratioH;
    }
    canvas.requestRenderAll();
};

const updateCanvasFocusPoint = (canvas, canvasBgImage) => {
    const vpt = canvas.viewportTransform;

    vpt[4] = getCanvasFocusOffset(canvas, canvasBgImage).offsetX;
    vpt[5] = getCanvasFocusOffset(canvas, canvasBgImage).offsetY;
};

const getCanvasFocusOffset = (canvas, canvasBgImage) => {
    const {
        bgImageWidth,
        bgImageHeight,
    } = getCanvasBgImageCalculatedSize(canvas, canvasBgImage);

    const canvasWidth = canvas.getWidth();
    const canvasHeight = canvas.getHeight();

    const offsetX = Math.max(0, (canvasWidth - bgImageWidth) / 2);
    const offsetY = Math.max(0, (canvasHeight - bgImageHeight) / 2);

    return {
        offsetX,
        offsetY,
    };
};

const getCanvasBgImageCalculatedSize = (canvas, canvasBgImage) => {
    return {
        bgImageWidth: canvasBgImage.width * canvasBgImage.scaleX * canvas.getZoom(),
        bgImageHeight: canvasBgImage.height * canvasBgImage.scaleY * canvas.getZoom(),
    };
};

const fitCanvasVptScale = (canvas) => {
    const ratio = getScaleRatio();
    const vpt = canvas.viewportTransform;
    vpt[0] = ratio;
    vpt[3] = ratio;
    canvas.requestRenderAll();
};

const getScaleRatio = () => {
    const boundingBox = globalThis.boundingBox;
    const h1 = boundingBox.height;
    const w1 = boundingBox.width;
    const h2 = innerHeight - 78;
    const w2 = innerWidth - 42;

    return Math.min(w2 / w1, h2 / h1);
};
