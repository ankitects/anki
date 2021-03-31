import IconButton from "./IconButton.svelte";
import ColorPicker from "./ColorPicker.svelte";

import { withLazyProperties } from "anki/lazy";
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

const forecolorButton = withLazyProperties(
    {
        component: IconButton,
        icon: squareFillIcon,
        className: "forecolor",
        onClick: () => wrapWithForecolor(getForecolor()),
    },
    {
        title: tr.editingSetForegroundColourF7,
    }
);

const colorpickerButton = withLazyProperties(
    {
        component: ColorPicker,
        className: "rainbow",
        onChange: ({ currentTarget }) => setForegroundColor(currentTarget.value),
    },
    {
        title: tr.editingChangeColourF8,
    }
);

export const colorButtons = [forecolorButton, colorpickerButton];
