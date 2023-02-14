// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type fabric from "fabric";

import { mdiRedo, mdiUndo } from "../icons";

export let stack: fabric.Object[] = [];
let isRedoing = false;

export const undoRedoInit = (canvas: fabric.Canvas): void => {
    canvas.on("object:added", function() {
        if (!isRedoing) {
            stack = [];
        }
        isRedoing = false;
    });
};

export const undoAction = (canvas: fabric.Canvas): void => {
    if (canvas._objects.length > 0) {
        stack.push(canvas._objects.pop());
        canvas.renderAll();
    }
};

export const redoAction = (canvas: fabric.Canvas): void => {
    if (stack.length > 0) {
        isRedoing = true;
        canvas.add(stack.pop());
    }
};

export const undoRedoTools = [
    {
        id: 1,
        icon: mdiUndo,
        action: undoAction,
    },
    {
        id: 2,
        icon: mdiRedo,
        action: redoAction,
    },
];
