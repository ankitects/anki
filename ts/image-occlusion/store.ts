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
