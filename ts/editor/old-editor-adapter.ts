// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { updateAllState } from "$lib/components/WithState.svelte";
import { execCommand } from "$lib/domlib";

import { filterHTML } from "../html-filter";

export function pasteHTML(
    html: string,
    internal: boolean,
    extendedMode: boolean,
): void {
    html = filterHTML(html, internal, extendedMode);

    if (html !== "") {
        setFormat("inserthtml", html);
    }
}

export function setFormat(cmd: string, arg?: string, _nosave = false): void {
    execCommand(cmd, false, arg);
    updateAllState(new Event(cmd));
}

export function toggleEditorButton(button: HTMLButtonElement): void {
    button.classList.toggle("active");
}
