// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// https://codepen.io/amsunny/pen/XWGLxye
// canvas.viewportTransform = [ scaleX, skewX, skewY, scaleY, translateX, translateY ]

import type { fabric } from "fabric";
import Hammer from "hammerjs";

import { getBoundingBox, redraw } from "./lib";

let isDragging = false;

const minScale = 0.5;
const maxScale = 5;
let zoomScale = 1;
export let currentScale = 1;

export const enableZoom = (canvas: fabric.Canvas) => {
    canvas.on("mouse:wheel", onMouseWheel);
    canvas.on("mouse:down", onMouseDown);
    canvas.on("mouse:move", onMouseMove);
    canvas.on("mouse:up", onMouseUp);
};

export const disableZoom = (canvas: fabric.Canvas) => {
    canvas.off("mouse:wheel", onMouseWheel);
    canvas.off("mouse:down", onMouseDown);
    canvas.off("mouse:move", onMouseMove);
    canvas.off("mouse:up", onMouseUp);
};

export const zoomIn = (canvas: fabric.Canvas): void => {
    let zoom = canvas.getZoom();
    zoom = Math.min(maxScale, zoom * 1.1);
    canvas.zoomToPoint({ x: canvas.width / 2, y: canvas.height / 2 }, zoom);
    constrainBoundsAroundBgImage(canvas);
    redraw(canvas);
};

export const zoomOut = (canvas): void => {
    let zoom = canvas.getZoom();
    zoom = Math.max(minScale, zoom / 1.1);
    canvas.zoomToPoint({ x: canvas.width / 2, y: canvas.height / 2 }, zoom / 1.1);
    constrainBoundsAroundBgImage(canvas);
    redraw(canvas);
};

export const zoomReset = (canvas: fabric.Canvas): void => {
    canvas.zoomToPoint({ x: canvas.width / 2, y: canvas.height / 2 }, 1);
    fitCanvasVptScale(canvas);
    constrainBoundsAroundBgImage(canvas);
};

export const enablePinchZoom = (canvas: fabric.Canvas) => {
    const hammer = new Hammer(canvas.upperCanvasEl);
    hammer.get("pinch").set({ enable: true });
    hammer.on("pinchin pinchout", ev => {
        currentScale = Math.min(Math.max(minScale, ev.scale * zoomScale), maxScale);
        canvas.zoomToPoint({ x: canvas.width / 2, y: canvas.height / 2 }, currentScale);
        constrainBoundsAroundBgImage(canvas);
        redraw(canvas);
    });
    hammer.on("pinchend pinchcancel", () => {
        zoomScale = currentScale;
    });
};

export const disablePinchZoom = (canvas: fabric.Canvas) => {
    const hammer = new Hammer(canvas.upperCanvasEl);
    hammer.get("pinch").set({ enable: false });
    hammer.off("pinch pinchmove pinchend pinchcancel");
};

export const onResize = (canvas: fabric.Canvas) => {
    setCanvasSize(canvas);
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
    constrainBoundsAroundBgImage(canvas);
    redraw(canvas);
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
    redraw(canvas);
};

export const onMouseMove = (opt) => {
    const canvas = globalThis.canvas;
    if (isDragging) {
        canvas.discardActiveObject();
        if (!canvas.viewportTransform) {
            return;
        }

        // handle pinch zoom and pan for mobile devices
        if (onPinchZoom(opt)) {
            return;
        }

        onDrag(canvas, opt);
    }
};

// initializes lastPosX and lastPosY because it is undefined in touchmove event
document.addEventListener("touchstart", (e) => {
    const canvas = globalThis.canvas;
    canvas.lastPosX = e.touches[0].clientX;
    canvas.lastPosY = e.touches[0].clientY;
});

export const onPinchZoom = (opt): boolean => {
    const { e } = opt;
    const canvas = globalThis.canvas;
    if ((e.type === "touchmove") && (e.touches.length > 1)) {
        onDrag(canvas, opt);
        return true;
    }
    return false;
};

const onDrag = (canvas, opt) => {
    const { e } = opt;
    const clientX = e.type === "touchmove" ? e.touches[0].clientX : e.clientX;
    const clientY = e.type === "touchmove" ? e.touches[0].clientY : e.clientY;
    const vpt = canvas.viewportTransform;

    vpt[4] += clientX - canvas.lastPosX;
    vpt[5] += clientY - canvas.lastPosY;
    canvas.lastPosX = clientX;
    canvas.lastPosY = clientY;
    constrainBoundsAroundBgImage(canvas);
    redraw(canvas);
};

const onMouseUp = () => {
    isDragging = false;
    const canvas = globalThis.canvas;
    canvas.setViewportTransform(canvas.viewportTransform);
    constrainBoundsAroundBgImage(canvas);
    redraw(canvas);
};

export const constrainBoundsAroundBgImage = (canvas: fabric.Canvas) => {
    const boundingBox = getBoundingBox();
    const ioImage = document.getElementById("image") as HTMLImageElement;

    const width = boundingBox.width * canvas.getZoom();
    const height = boundingBox.height * canvas.getZoom();

    const left = canvas.viewportTransform[4];
    const top = canvas.viewportTransform[5];

    ioImage.width = width;
    ioImage.height = height;
    ioImage.style.left = `${left}px`;
    ioImage.style.top = `${top}px`;
};

export const setCanvasSize = (canvas: fabric.Canvas) => {
    canvas.setHeight(window.innerHeight - 76);
    canvas.setWidth(window.innerWidth - 39);
    redraw(canvas);
};

const fitCanvasVptScale = (canvas: fabric.Canvas) => {
    const boundingBox = getBoundingBox();
    const ratio = getScaleRatio(boundingBox);
    const vpt = canvas.viewportTransform;

    const boundingBoxWidth = boundingBox.width * canvas.getZoom();
    const boundingBoxHeight = boundingBox.height * canvas.getZoom();
    const center = canvas.getCenter();
    const translateX = center.left - (boundingBoxWidth / 2);
    const translateY = center.top - (boundingBoxHeight / 2);

    vpt[0] = ratio;
    vpt[3] = ratio;
    vpt[4] = Math.max(1, translateX);
    vpt[5] = Math.max(1, translateY);

    canvas.setViewportTransform(canvas.viewportTransform);
    constrainBoundsAroundBgImage(canvas);
    redraw(canvas);
};

const getScaleRatio = (boundingBox: fabric.Rect) => {
    const h1 = boundingBox.height;
    const w1 = boundingBox.width;
    const h2 = innerHeight - 79;
    const w2 = innerWidth - 42;

    return Math.min(w2 / w1, h2 / h1);
};
