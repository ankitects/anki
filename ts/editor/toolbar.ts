// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { ToolbarItem, IterableToolbarItem } from "editor-toolbar/types";
import type { Writable } from "svelte/store";

import { getNotetypeGroup } from "./notetype";
import { getFormatInlineGroup } from "./formatInline";
import { getFormatBlockGroup, getFormatBlockMenus } from "./formatBlock";
import { getColorGroup } from "./color";
import { getTemplateGroup, getTemplateMenus } from "./template";

export function initToolbar(i18n: Promise<void>): void {
    document.addEventListener("DOMContentLoaded", () => {
        i18n.then(() => {
            globalThis.$editorToolbar.buttonsPromise.then(
                (
                    buttons: Writable<IterableToolbarItem[]>
                ): Writable<IterableToolbarItem[]> => {
                    buttons.update(() => [
                        getNotetypeGroup(),
                        getFormatInlineGroup(),
                        getFormatBlockGroup(),
                        getColorGroup(),
                        getTemplateGroup(),
                    ]);
                    return buttons;
                }
            );

            globalThis.$editorToolbar.menusPromise.then(
                (menus: Writable<ToolbarItem[]>): Writable<ToolbarItem[]> => {
                    menus.update(() => [
                        ...getFormatBlockMenus(),
                        ...getTemplateMenus(),
                    ]);
                    return menus;
                }
            );
        });
    });
}
