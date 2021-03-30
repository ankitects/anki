// @ts-ignore
import CommandIconButton, { updateButtonActive } from "./CommandIconButton.svelte";
import boldIcon from "./type-bold.svg";
import italicIcon from "./type-italic.svg";
import underlineIcon from "./type-underline.svg";
import superscriptIcon from "./format-superscript.svg";
import subscriptIcon from "./format-subscript.svg";
import eraserIcon from "./eraser.svg";

export const boldButton = {
    component: CommandIconButton,
    icon: boldIcon,
    command: "bold",
};

export const italicButton = {
    component: CommandIconButton,
    icon: italicIcon,
    command: "italic",
};

export const underlineButton = {
    component: CommandIconButton,
    icon: underlineIcon,
    command: "underline",
};

export const superscriptButton = {
    component: CommandIconButton,
    icon: superscriptIcon,
    command: "superscript",
};

export const subscriptButton = {
    component: CommandIconButton,
    icon: subscriptIcon,
    command: "subscript",
};

export const eraserButton = {
    component: CommandIconButton,
    icon: eraserIcon,
    command: "removeFormat",
    highlightable: false,
};

// TODO
setInterval(updateButtonActive, 2000);
