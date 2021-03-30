import IconButton from "./IconButton.svelte";
import ColorPicker from "./ColorPicker.svelte";
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

export const forecolorButton = {
    component: IconButton,
    icon: squareFillIcon,
    className: "forecolor",
    onClick: () => wrapWithForecolor(getForecolor()),
};

export const colorpickerButton = {
    component: ColorPicker,
    className: "rainbow",
    onChange: ({ currentTarget }) => setForegroundColor(currentTarget.value),
};
