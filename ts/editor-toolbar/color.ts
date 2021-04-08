import IconButton from "./IconButton.svelte";
import type { IconButtonProps } from "./IconButton";
import ColorPicker from "./ColorPicker.svelte";
import type { ColorPickerProps } from "./ColorPicker";
import ButtonGroup from "./ButtonGroup.svelte";
import type { ButtonGroupProps } from "./ButtonGroup";

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
const forecolorButton = iconButton<IconButtonProps, "tooltip">(
    {
        icon: squareFillIcon,
        className: "forecolor",
        onClick: () => wrapWithForecolor(getForecolor()),
    },
    {
        tooltip: tr.editingSetForegroundColourF7,
    }
);

const colorPicker = dynamicComponent(ColorPicker);
const colorpickerButton = colorPicker<ColorPickerProps, "tooltip">(
    {
        className: "rainbow",
        onChange: ({ currentTarget }) =>
            setForegroundColor((currentTarget as HTMLInputElement).value),
    },
    {
        tooltip: tr.editingChangeColourF8,
    }
);

const buttonGroup = dynamicComponent(ButtonGroup);
export const colorGroup = buttonGroup<ButtonGroupProps>(
    {
        id: "color",
        buttons: [forecolorButton, colorpickerButton],
    },
    {}
);
