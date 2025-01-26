// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@generated/ftl";
import { fabric } from "fabric";
import { writable } from "svelte/store";

import { mdiRedo, mdiUndo } from "$lib/components/icons";

import { saveNeededStore } from "../store";
import { redoKeyCombination, undoKeyCombination } from "./shortcuts";
import { removeUnfinishedPolygon } from "./tool-polygon";

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
    if (shape.width! <= 5 || shape.height! <= 5) {
        return false;
    }
    if (shapeType.indexOf(shape.type!) === -1) {
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
            if (!opts.target!.group && !opts.target!.destroyed) {
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
            saveNeededStore.set(true);
            this.locked = false;
        });
        // make bounding box unselectable
        this.canvas?.forEachObject((obj) => {
            if (obj instanceof fabric.Rect && obj.fill === "transparent") {
                obj.selectable = false;
            }
        });
    }

    onObjectAdded(id: string): void {
        if (!this.shapeIds.has(id)) {
            this.push();
        }
        this.shapeIds.add(id);
        saveNeededStore.set(true);
    }

    onObjectModified(): void {
        this.push();
        saveNeededStore.set(true);
    }

    private maybePush(obj: fabric.IEvent<MouseEvent>): void {
        if (!this.locked && validShape(obj.target!)) {
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
        if (this.canvas && removeUnfinishedPolygon(this.canvas)) {
            // treat removing the unfinished polygon as an undo step
            return;
        }
        if (this.canUndo()) {
            this.index--;
            this.updateState();
            this.updateCanvas();
        }
    }

    redo(): void {
        if (this.canvas) {
            // when redoing, removing an unfinished polygon doesn't make sense as a discrete step
            removeUnfinishedPolygon(this.canvas);
        }
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
