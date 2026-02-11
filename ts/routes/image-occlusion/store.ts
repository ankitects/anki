// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { writable } from "svelte/store";

export enum OcclusionMode {
    HideOne = 0,
    HideAll = 1,
    HideAllButOne = 2,
}

// it stores note's data for generate.ts, when function generate() is called it will be used to generate the note
export const notesDataStore = writable({ id: "", title: "", divValue: "", textareaValue: "" }[0]);
// it stores the tags for the note in note editor
export const tagsWritable = writable([""]);
// it stores the visibility of mask editor
export const ioMaskEditorVisible = writable(true);
// it stores the occlusion mode (hide one, hide all, or hide all reveal one)
export const occlusionMode = writable(OcclusionMode.HideAll);
// ioImageLoadedStore is used to store the image loaded event
export const ioImageLoadedStore = writable(false);
// store opacity state of objects in canvas
export const opacityStateStore = writable(false);
// store state of text editing
export const textEditingState = writable(false);
// Stores if the canvas shapes data needs to be saved
export const saveNeededStore = writable(false);
