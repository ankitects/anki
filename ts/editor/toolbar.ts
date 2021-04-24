// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { getNotetypeGroup } from "./notetype";
import { getFormatInlineGroup } from "./formatInline";
import { getFormatBlockGroup, getFormatBlockMenus } from "./formatBlock";
import { getColorGroup } from "./color";
import { getTemplateGroup, getTemplateMenus } from "./template";

export function initToolbar(i18n: Promise<void>): void {
    document.addEventListener("DOMContentLoaded", () => {
        i18n.then(() => {
            const buttons = [
                getNotetypeGroup(),
                getFormatInlineGroup(),
                getFormatBlockGroup(),
                getColorGroup(),
                getTemplateGroup(),
            ];

            const menus = [...getFormatBlockMenus(), ...getTemplateMenus()];

            globalThis.$editorToolbar.updateButton(() => ({ items: buttons }));
            globalThis.$editorToolbar.updateMenu(() => ({ items: menus }));
        });
    });
}
