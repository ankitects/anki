// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type fabric from "fabric";

import { mdiRedo, mdiUndo } from "../icons";

/**
 * Undo redo for rectangle and ellipse handled here,
 * view tool-polygon for handling undo redo in case of polygon
 */

let lockHistory = false;
const undoHistory: string[] = [];
const redoHistory: string[] = [];

const shapeType = ["rect", "ellipse"];

export const undoRedoInit = (canvas: fabric.Canvas): void => {
    undoHistory.push(JSON.stringify(canvas));

    canvas.on("object:modified", function(o) {
        if (lockHistory) return;
        if (!validShape(o.target as fabric.Object)) return;
        saveCanvasState(canvas);
    });

    canvas.on("object:removed", function(o) {
        if (lockHistory) return;
        if (!validShape(o.target as fabric.Object)) return;
        saveCanvasState(canvas);
    });
};

const validShape = (shape: fabric.Object): boolean => {
    if (shape.width <= 5 || shape.height <= 5) return false;
    if (shapeType.indexOf(shape.type) === -1) return false;
    return true;
};

export const undoAction = (canvas: fabric.Canvas): void => {
    if (undoHistory.length > 0) {
        lockHistory = true;
        if (undoHistory.length > 1) redoHistory.push(undoHistory.pop() as string);
        const content = undoHistory[undoHistory.length - 1];
        canvas.loadFromJSON(content, function() {
            canvas.renderAll();
            lockHistory = false;
        });
    }
};

export const redoAction = (canvas: fabric.Canvas): void => {
    if (redoHistory.length > 0) {
        lockHistory = true;
        const content = redoHistory.pop() as string;
        undoHistory.push(content);
        canvas.loadFromJSON(content, function() {
            canvas.renderAll();
            lockHistory = false;
        });
    }
};

export const objectAdded = (canvas: fabric.Canvas, shapeIdList: string[], shapeId: string): void => {
    if (shapeIdList.includes(shapeId)) {
        return;
    }

    shapeIdList.push(shapeId);
    saveCanvasState(canvas);
};

export const saveCanvasState = (canvas: fabric.Canvas): void => {
    undoHistory.push(JSON.stringify(canvas));
    redoHistory.length = 0;
};

export const undoRedoTools = [
    {
        name: "undo",
        icon: mdiUndo,
        action: undoAction,
    },
    {
        name: "redo",
        icon: mdiRedo,
        action: redoAction,
    },
];
