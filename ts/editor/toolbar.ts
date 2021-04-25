// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { editorToolbar, EditorToolbarAPI } from "editor-toolbar";

import { getNotetypeGroup } from "./notetype";
import { getFormatInlineGroup } from "./formatInline";
import { getFormatBlockGroup, getFormatBlockMenus } from "./formatBlock";
import { getColorGroup } from "./color";
import { getTemplateGroup, getTemplateMenus } from "./template";

export function initToolbar(i18n: Promise<void>): Promise<EditorToolbarAPI> {
    let toolbarResolve: (value: EditorToolbarAPI) => void;
    const toolbarPromise = new Promise<EditorToolbarAPI>((resolve) => {
        toolbarResolve = resolve;
    });

    document.addEventListener("DOMContentLoaded", () => {
        i18n.then(() => {
            const target = document.getElementById("editorToolbar")!;

            const buttons = [
                getNotetypeGroup(),
                getFormatInlineGroup(),
                getFormatBlockGroup(),
                getColorGroup(),
                getTemplateGroup(),
            ];

            const menus = [...getFormatBlockMenus(), ...getTemplateMenus()];

            toolbarResolve(editorToolbar(target, buttons, menus));
        });
    });

    return toolbarPromise;
}
