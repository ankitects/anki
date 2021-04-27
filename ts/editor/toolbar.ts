// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { editorToolbar, EditorToolbar } from "editor-toolbar";

// import { getNotetypeGroup } from "./notetype";
// import { getFormatInlineGroup } from "./formatInline";
// import { getFormatBlockGroup, getFormatBlockMenus } from "./formatBlock";
// import { getColorGroup } from "./color";
// import { getTemplateGroup, getTemplateMenus } from "./template";

export function initToolbar(i18n: Promise<void>) {
    let toolbarResolve: (value: EditorToolbar) => void;
    const toolbarPromise = new Promise<EditorToolbar>((resolve) => {
        toolbarResolve = resolve;
    });

    document.addEventListener("DOMContentLoaded", () => {
        i18n.then(() => {
            const target = document.body;
            const anchor = document.getElementById("fields")!;

            const buttons = [
                // getNotetypeGroup(),
                // getFormatInlineGroup(),
                // getFormatBlockGroup(),
                // getColorGroup(),
                // getTemplateGroup(),
            ];

            const menus = [
                /*...getFormatBlockMenus(), ...getTemplateMenus()*/
            ];

            toolbarResolve(editorToolbar({ target, anchor, buttons, menus }));
        });
    });

    return toolbarPromise;
}
