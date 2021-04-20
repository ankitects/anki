// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import IconButton from "editor-toolbar/IconButton.svelte";
import type { IconButtonProps } from "editor-toolbar/IconButton";
import ColorPicker from "editor-toolbar/ColorPicker.svelte";
import type { ColorPickerProps } from "editor-toolbar/ColorPicker";
import ButtonGroup from "editor-toolbar/ButtonGroup.svelte";
import type { ButtonGroupProps } from "editor-toolbar/ButtonGroup";

import { DynamicSvelteComponent, dynamicComponent } from "sveltelib/dynamicComponent";
import * as tr from "anki/i18n";

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

const iconButton = dynamicComponent<typeof IconButton, IconButtonProps>(IconButton);
const colorPicker = dynamicComponent<typeof ColorPicker, ColorPickerProps>(ColorPicker);
const buttonGroup = dynamicComponent<typeof ButtonGroup, ButtonGroupProps>(ButtonGroup);

export function getColorGroup(): DynamicSvelteComponent<typeof ButtonGroup> &
    ButtonGroupProps {
    const forecolorButton = iconButton({
        icon: squareFillIcon,
        className: "forecolor",
        onClick: () => wrapWithForecolor(getForecolor()),
        tooltip: tr.editingSetForegroundColourF7(),
    });

    const colorpickerButton = colorPicker({
        onChange: ({ currentTarget }) =>
            setForegroundColor((currentTarget as HTMLInputElement).value),
        tooltip: tr.editingChangeColourF8(),
    });

    return buttonGroup({
        id: "color",
        buttons: [forecolorButton, colorpickerButton],
    });
}
