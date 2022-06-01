// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { updateAllState } from "../components/WithState.svelte";
import { execCommand } from "../domlib";
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
