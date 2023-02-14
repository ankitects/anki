// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { writable } from "svelte/store";

export const active = writable("");
export const notesDataStore = writable({ id: "", title: "", divValue: "", textareaValue: "" }[0]);
export const zoomResetValue = writable(1);
export const tagsWritable = writable([""]);
