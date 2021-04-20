import type { ToolbarItem } from "editor-toolbar/types";
import type ButtonGroup from "editor-toolbar/ButtonGroup.svelte";
import type { ButtonGroupProps } from "editor-toolbar/ButtonGroup";
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
                    buttons: Writable<
                        (ToolbarItem<typeof ButtonGroup> & ButtonGroupProps)[]
                    >
                ): Writable<(ToolbarItem<typeof ButtonGroup> & ButtonGroupProps)[]> => {
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
