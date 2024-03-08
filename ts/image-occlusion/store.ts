// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { writable } from "svelte/store";

// it stores note's data for generate.ts, when function generate() is called it will be used to generate the note
export const notesDataStore = writable({ id: "", title: "", divValue: "", textareaValue: "" }[0]);
// it stores the value of zoom ratio for canvas
export const zoomResetValue = writable(1);
// it stores the tags for the note in note editor
export const tagsWritable = writable([""]);
// it stores the visibility of mask editor
export const ioMaskEditorVisible = writable(true);
// it store hide all or hide one mode
export const hideAllGuessOne = writable(true);
// store initial value of x for zoom reset
export const zoomResetX = writable(0);
// ioImageLoadedStore is used to store the image loaded event
export const ioImageLoadedStore = writable(false);
// store opacity state of objects in canvas
export const opacityStateStore = writable(false);
// store state of text editing
export const textEditingState = writable(false);
// store color and size of line tool
export const lineToolConfig = writable({ color: "#000000", size: 5 });
// store color and size of path tool
export const pathToolConfig = writable({ color: "#000000", size: 5 });
