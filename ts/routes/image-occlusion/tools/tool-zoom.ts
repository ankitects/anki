// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// https://codepen.io/amsunny/pen/XWGLxye
// canvas.viewportTransform = [ scaleX, skewX, skewY, scaleY, translateX, translateY ]

import type { fabric } from "fabric";
import Hammer from "hammerjs";

import { isDesktop } from "$lib/tslib/platform";

import type { Size } from "../types";
import { getBoundingBoxSize, redraw } from "./lib";

const minScale = 0.5;
const maxScale = 5;
let zoomScale = 1;
export let currentScale = 1;

export const enableZoom = (canvas: fabric.Canvas) => {
    canvas.on("mouse:wheel", onMouseWheel);
};

export const enablePan = (canvas: fabric.Canvas) => {
    canvas.on("mouse:down", onMouseDown);
    canvas.on("mouse:move", onMouseMove);
    canvas.on("mouse:up", onMouseUp);
};

export const disableZoom = (canvas: fabric.Canvas) => {
    canvas.off("mouse:wheel", onMouseWheel);
};

export const disablePan = (canvas: fabric.Canvas) => {
    canvas.off("mouse:down", onMouseDown);
    canvas.off("mouse:move", onMouseMove);
    canvas.off("mouse:up", onMouseUp);
};

export const zoomIn = (canvas: fabric.Canvas): void => {
    let zoom = canvas.getZoom();
    zoom = Math.min(maxScale, zoom * 1.1);
    canvas.zoomToPoint({ x: canvas.width! / 2, y: canvas.height! / 2 }, zoom);
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
    zoomResetInner(canvas);
    // reset again to update the viewportTransform
    zoomResetInner(canvas);
};

const zoomResetInner = (canvas: fabric.Canvas): void => {
    fitCanvasVptScale(canvas);
    const vpt = canvas.viewportTransform!;
    canvas.zoomToPoint({ x: canvas.width! / 2, y: canvas.height! / 2 }, vpt[0]);
};

export const enablePinchZoom = (canvas: fabric.Canvas) => {
    const hammer = new Hammer(upperCanvasElement(canvas));
    hammer.get("pinch").set({ enable: true });
    hammer.on("pinchin pinchout", ev => {
        currentScale = Math.min(Math.max(minScale, ev.scale * zoomScale), maxScale);
        canvas.zoomToPoint({ x: canvas.width! / 2, y: canvas.height! / 2 }, currentScale);
        constrainBoundsAroundBgImage(canvas);
        redraw(canvas);
    });
    hammer.on("pinchend pinchcancel", () => {
        zoomScale = currentScale;
    });
};

function upperCanvasElement(canvas: fabric.Canvas): HTMLElement {
    return canvas["upperCanvasEl"] as HTMLElement;
}

export const disablePinchZoom = (canvas: fabric.Canvas) => {
    const hammer = new Hammer(upperCanvasElement(canvas));
    hammer.get("pinch").set({ enable: false });
    hammer.off("pinch pinchmove pinchend pinchcancel");
};

export const onResize = (canvas: fabric.Canvas) => {
    setCanvasSize(canvas);
    zoomReset(canvas);
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
    canvas.discardActiveObject();
    if (!canvas.viewportTransform) {
        return;
    }

    // handle pinch zoom and pan for mobile devices
    if (onPinchZoom(opt)) {
        return;
    }

    onDrag(canvas, opt);
};

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
    canvas.lastPosX += clientX - canvas.lastPosX;
    canvas.lastPosY += clientY - canvas.lastPosY;
    constrainBoundsAroundBgImage(canvas);
    redraw(canvas);
};

export const onWheelDrag = (canvas: fabric.Canvas, event: WheelEvent) => {
    const deltaX = event.deltaX;
    const deltaY = event.deltaY;
    const vpt = canvas.viewportTransform!;
    canvas["lastPosX"] = event.clientX;
    canvas["lastPosY"] = event.clientY;

    vpt[4] -= deltaX;
    vpt[5] -= deltaY;

    canvas["lastPosX"] -= deltaX;
    canvas["lastPosY"] -= deltaY;
    canvas.setViewportTransform(vpt);
    constrainBoundsAroundBgImage(canvas);
    redraw(canvas);
};

export const onWheelDragX = (canvas: fabric.Canvas, event: WheelEvent) => {
    const delta = event.deltaY;
    const vpt = canvas.viewportTransform!;
    (canvas as any).lastPosY = event.clientY!;
    vpt[4] -= delta;
    (canvas as any).lastPosX -= delta;
    canvas.setViewportTransform(vpt);
    constrainBoundsAroundBgImage(canvas);
    redraw(canvas);
};

const onMouseUp = () => {
    const canvas = globalThis.canvas;
    canvas.setViewportTransform(canvas.viewportTransform);
    constrainBoundsAroundBgImage(canvas);
    redraw(canvas);
};

export const constrainBoundsAroundBgImage = (canvas: fabric.Canvas) => {
    const boundingBox = getBoundingBoxSize();
    const ioImage = document.getElementById("image") as HTMLImageElement;

    const width = boundingBox.width * canvas.getZoom();
    const height = boundingBox.height * canvas.getZoom();

    const left = canvas.viewportTransform![4];
    const top = canvas.viewportTransform![5];

    ioImage.width = width;
    ioImage.height = height;
    ioImage.style.left = `${left}px`;
    ioImage.style.top = `${top}px`;
};

export const setCanvasSize = (canvas: fabric.Canvas) => {
    const width = window.innerWidth - 39;
    let height = window.innerHeight;
    height = isDesktop() ? height - 76 : height - 46;
    canvas.setHeight(height);
    canvas.setWidth(width);
    redraw(canvas);
};

const fitCanvasVptScale = (canvas: fabric.Canvas) => {
    const boundingBox = getBoundingBoxSize();
    const ratio = getScaleRatio(boundingBox);
    const vpt = canvas.viewportTransform!;

    const boundingBoxWidth = boundingBox.width * canvas.getZoom();
    const boundingBoxHeight = boundingBox.height * canvas.getZoom();
    const center = canvas.getCenter();
    const translateX = center.left - (boundingBoxWidth / 2);
    const translateY = center.top - (boundingBoxHeight / 2);

    vpt[0] = ratio;
    vpt[3] = ratio;
    vpt[4] = Math.max(1, translateX);
    vpt[5] = Math.max(1, translateY);

    canvas.setViewportTransform(canvas.viewportTransform!);
    constrainBoundsAroundBgImage(canvas);
    redraw(canvas);
};

const getScaleRatio = (boundingBox: Size) => {
    const h1 = boundingBox.height!;
    const w1 = boundingBox.width!;
    const w2 = innerWidth - 42;
    let h2 = window.innerHeight;
    h2 = isDesktop() ? h2 - 79 : h2 - 48;
    return Math.min(w2 / w1, h2 / h1);
};
