// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { IterableToolbarItem } from "sveltelib/types";

import { buttonGroup } from "sveltelib/dynamicComponents";
import {
    iconButton,
    colorPicker,
    withShortcut,
} from "editor-toolbar/dynamicComponents";
import * as tr from "lib/i18n";

import squareFillIcon from "./square-fill.svg";
import "./color.css";

const foregroundColorKeyword = "--foreground-color";

function setForegroundColor(color: string): void {
    document.documentElement.style.setProperty(foregroundColorKeyword, color);
}

function getForecolor(): string {
    return document.documentElement.style.getPropertyValue(foregroundColorKeyword);
}

function wrapWithForecolor(color: string): void {
    document.execCommand("forecolor", false, color);
}

export function getColorGroup(): IterableToolbarItem {
    const forecolorButton = withShortcut({
        shortcut: "F7",
        button: iconButton({
            icon: squareFillIcon,
            className: "forecolor",
            onClick: () => wrapWithForecolor(getForecolor()),
            tooltip: tr.editingSetForegroundColor(),
        }),
    });

    const colorpickerButton = withShortcut({
        shortcut: "F8",
        button: colorPicker({
            onChange: ({ currentTarget }) =>
                setForegroundColor((currentTarget as HTMLInputElement).value),
            tooltip: tr.editingChangeColor(),
        }),
    });

    return buttonGroup({
        id: "color",
        items: [forecolorButton, colorpickerButton],
    });
}
