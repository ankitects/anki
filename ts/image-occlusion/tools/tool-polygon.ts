// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";
import { opacityStateStore } from "image-occlusion/store";
import type { PanZoom } from "panzoom";
import { get } from "svelte/store";

import { BORDER_COLOR, SHAPE_MASK_COLOR } from "./lib";
import { undoStack } from "./tool-undo-redo";

let activeLine;
let activeShape;
let linesList: fabric.Line = [];
let pointsList: fabric.Circle = [];
let drawMode = false;
let zoomValue = 1;

export const drawPolygon = (canvas: fabric.Canvas, panzoom: PanZoom): void => {
    // remove selectable for shapes
    canvas.discardActiveObject();
    canvas.forEachObject(function(o) {
        o.selectable = false;
    });

    canvas.selectionColor = "rgba(0, 0, 0, 0)";
    canvas.on("mouse:down", function(options) {
        try {
            if (options.target && options.target.id === pointsList[0].id) {
                generatePolygon(canvas, pointsList);
            } else {
                addPoint(canvas, options, panzoom);
            }
        } catch (e) {
            // Cannot read properties of undefined (reading 'id')
        }
    });

    canvas.on("mouse:move", function(options) {
        if (activeLine && activeLine.class === "line") {
            const pointer = canvas.getPointer(options.e);
            activeLine.set({
                x2: pointer.x,
                y2: pointer.y,
            });

            const points = activeShape.get("points");
            points[pointsList.length] = {
                x: pointer.x,
                y: pointer.y,
            };

            activeShape.set({ points });
        }
        canvas.renderAll();
    });
};

const toggleDrawPolygon = (canvas: fabric.Canvas): void => {
    drawMode = !drawMode;
    if (drawMode) {
        activeLine = null;
        activeShape = null;
        linesList = [];
        pointsList = [];
        drawMode = false;
        canvas.selection = true;
    } else {
        drawMode = true;
        canvas.selection = false;
    }
};

const addPoint = (canvas: fabric.Canvas, options, panzoom): void => {
    zoomValue = panzoom.getTransform().scale;

    const canvasContainer = document.querySelector(".canvas-container")!.getBoundingClientRect()!;
    let clientX = options.e.touches ? options.e.touches[0].clientX : options.e.clientX;
    let clientY = options.e.touches ? options.e.touches[0].clientY : options.e.clientY;

    clientX = (clientX - canvasContainer.left) / zoomValue;
    clientY = (clientY - canvasContainer.top) / zoomValue;

    const point = new fabric.Circle({
        radius: 5,
        fill: "#ffffff",
        stroke: "#333333",
        strokeWidth: 0.5,
        originX: "left",
        originY: "top",
        left: clientX,
        top: clientY,
        selectable: false,
        hasBorders: false,
        hasControls: false,
        objectCaching: false,
    });

    if (pointsList.length === 0) {
        point.set({
            fill: "red",
        });
    }

    const linePoints = [clientX, clientY, clientX, clientY];

    const line = new fabric.Line(linePoints, {
        strokeWidth: 2,
        fill: "#999999",
        stroke: "#999999",
        originX: "left",
        originY: "top",
        selectable: false,
        hasBorders: false,
        hasControls: false,
        evented: false,
        objectCaching: false,
    });
    line.class = "line";

    if (activeShape) {
        const pointer = canvas.getPointer(options.e);
        const points = activeShape.get("points");
        points.push({
            x: pointer.x,
            y: pointer.y,
        });

        const polygon = new fabric.Polygon(points, {
            stroke: "#333333",
            strokeWidth: 1,
            fill: "#cccccc",
            opacity: 0.3,
            selectable: false,
            hasBorders: false,
            hasControls: false,
            evented: false,
            objectCaching: false,
        });

        canvas.remove(activeShape);
        canvas.add(polygon);
        activeShape = polygon;
        canvas.renderAll();
    } else {
        const polyPoint = [{ x: clientX, y: clientY }];
        const polygon = new fabric.Polygon(polyPoint, {
            stroke: "#333333",
            strokeWidth: 1,
            fill: "#cccccc",
            opacity: 0.3,
            selectable: false,
            hasBorders: false,
            hasControls: false,
            evented: false,
            objectCaching: false,
        });

        activeShape = polygon;
        canvas.add(polygon);
    }

    activeLine = line;
    pointsList.push(point);
    linesList.push(line);

    canvas.add(line);
    canvas.add(point);
};

const generatePolygon = (canvas: fabric.Canvas, pointsList): void => {
    const points: { x: number; y: number }[] = [];
    pointsList.forEach((point) => {
        points.push({
            x: point.left,
            y: point.top,
        });
        canvas.remove(point);
    });

    linesList.forEach((line) => {
        canvas.remove(line);
    });

    canvas.remove(activeShape).remove(activeLine);

    const polygon = new fabric.Polygon(points, {
        id: "polygon-" + new Date().getTime(),
        fill: SHAPE_MASK_COLOR,
        objectCaching: false,
        stroke: BORDER_COLOR,
        strokeWidth: 1,
        strokeUniform: true,
        noScaleCache: false,
        opacity: get(opacityStateStore) ? 0.4 : 1,
    });
    if (polygon.width > 5 && polygon.height > 5) {
        canvas.add(polygon);
        canvas.setActiveObject(polygon);
        // view undo redo tools
        undoStack.onObjectAdded(polygon.id);
    }

    toggleDrawPolygon(canvas);
};

// https://github.com/fabricjs/fabric.js/issues/6522
export const modifiedPolygon = (canvas: fabric.Canvas, polygon: fabric.Polygon): void => {
    const matrix = polygon.calcTransformMatrix();
    const transformedPoints = polygon.get("points")
        .map(function(p) {
            return new fabric.Point(p.x - polygon.pathOffset.x, p.y - polygon.pathOffset.y);
        })
        .map(function(p) {
            return fabric.util.transformPoint(p, matrix);
        });

    const polygon1 = new fabric.Polygon(transformedPoints, {
        id: polygon.id,
        fill: SHAPE_MASK_COLOR,
        objectCaching: false,
        stroke: BORDER_COLOR,
        strokeWidth: 1,
        strokeUniform: true,
        noScaleCache: false,
        opacity: get(opacityStateStore) ? 0.4 : 1,
    });

    canvas.remove(polygon);
    canvas.add(polygon1);
};

export const removeUnfinishedPolygon = (canvas: fabric.Canvas): void => {
    canvas.remove(activeShape).remove(activeLine);
    pointsList.forEach((point) => {
        canvas.remove(point);
    });
    linesList.forEach((line) => {
        canvas.remove(line);
    });
    activeLine = null;
    activeShape = null;
    linesList = [];
    pointsList = [];
    drawMode = false;
    canvas.selection = true;
};
