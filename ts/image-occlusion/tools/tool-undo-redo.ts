// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";
import type fabric from "fabric";
import { writable } from "svelte/store";

import { mdiRedo, mdiUndo } from "../icons";
import { emitChangeSignal } from "../MaskEditor.svelte";
import { redoKeyCombination, undoKeyCombination } from "./shortcuts";

/**
 * Undo redo for rectangle and ellipse handled here,
 * view tool-polygon for handling undo redo in case of polygon
 */

type UndoState = {
    undoable: boolean;
    redoable: boolean;
};

const shapeType = ["rect", "ellipse", "i-text", "group"];

const validShape = (shape: fabric.Object): boolean => {
    if (shape.width <= 5 || shape.height <= 5) {
        return false;
    }
    if (shapeType.indexOf(shape.type) === -1) {
        return false;
    }
    return true;
};

class UndoStack {
    private stack: string[] = [];
    private index = -1;
    private canvas: fabric.Canvas | undefined;
    private locked = false;
    private shapeIds = new Set<string>();
    /** used to make the toolbar buttons reactive */
    private state = writable<UndoState>({ undoable: false, redoable: false });
    subscribe: typeof this.state.subscribe;

    constructor() {
        // allows an instance of the class to act as a store
        this.subscribe = this.state.subscribe;
    }

    setCanvas(canvas: fabric.Canvas): void {
        this.canvas = canvas;
        this.canvas.on("object:modified", (opts) => this.maybePush(opts));
        this.canvas.on("object:removed", (opts) => {
            // `destroyed` is a custom property set on groups in the ungrouping routine to avoid adding a spurious undo entry
            if (!opts.target.group && !opts.target.destroyed) {
                this.maybePush(opts);
            }
        });
    }

    reset(): void {
        this.shapeIds.clear();
        this.stack.length = 0;
        this.index = -1;
        this.push();
        this.updateState();
    }

    private canUndo(): boolean {
        return this.index > 0;
    }

    private canRedo(): boolean {
        return this.index < this.stack.length - 1;
    }

    private updateState(): void {
        this.state.set({
            undoable: this.canUndo(),
            redoable: this.canRedo(),
        });
    }

    private updateCanvas(): void {
        this.locked = true;
        this.canvas?.loadFromJSON(this.stack[this.index], () => {
            this.canvas?.renderAll();
            emitChangeSignal();
            this.locked = false;
        });
    }

    onObjectAdded(id: string): void {
        if (!this.shapeIds.has(id)) {
            this.push();
        }
        this.shapeIds.add(id);
        emitChangeSignal();
    }

    onObjectModified(): void {
        this.push();
        emitChangeSignal();
    }

    private maybePush(opts): void {
        if (!this.locked && validShape(opts.target as fabric.Object)) {
            this.push();
        }
    }

    private push(): void {
        const entry = JSON.stringify(this.canvas);
        if (entry === this.stack[this.stack.length - 1]) {
            return;
        }
        this.stack.length = this.index + 1;
        this.stack.push(entry);
        this.index++;
        this.updateState();
    }

    undo(): void {
        if (this.canUndo()) {
            this.index--;
            this.updateState();
            this.updateCanvas();
        }
    }

    redo(): void {
        if (this.canRedo()) {
            this.index++;
            this.updateState();
            this.updateCanvas();
        }
    }
}

export const undoStack = new UndoStack();

export const undoRedoTools = [
    {
        name: "undo",
        icon: mdiUndo,
        action: () => undoStack.undo(),
        tooltip: tr.undoUndo,
        shortcut: undoKeyCombination,
    },
    {
        name: "redo",
        icon: mdiRedo,
        action: () => undoStack.redo(),
        tooltip: tr.undoRedo,
        shortcut: redoKeyCombination,
    },
];
