import IconButton from "./IconButton.svelte";
import type { IconButtonProps } from "./IconButton";
import ColorPicker from "./ColorPicker.svelte";
import type { ColorPickerProps } from "./ColorPicker";

import { dynamicComponent } from "sveltelib/dynamicComponent";
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

const iconButton = dynamicComponent(IconButton);
const forecolorButton = iconButton<IconButtonProps, "title">(
    {
        icon: squareFillIcon,
        className: "forecolor",
        onClick: () => wrapWithForecolor(getForecolor()),
    },
    {
        title: tr.editingSetForegroundColourF7,
    }
);

const colorPicker = dynamicComponent(ColorPicker);
const colorpickerButton = colorPicker<ColorPickerProps, "title">(
    {
        className: "rainbow",
        onChange: ({ currentTarget }) =>
            setForegroundColor((currentTarget as HTMLInputElement).value),
    },
    {
        title: tr.editingChangeColourF8,
    }
);

export const colorButtons = [forecolorButton, colorpickerButton];
