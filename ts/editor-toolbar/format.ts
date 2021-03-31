// @ts-ignore
import CommandIconButton from "./CommandIconButton.svelte";
import boldIcon from "./type-bold.svg";
import italicIcon from "./type-italic.svg";
import underlineIcon from "./type-underline.svg";
import superscriptIcon from "./format-superscript.svg";
import subscriptIcon from "./format-subscript.svg";
import eraserIcon from "./eraser.svg";

const boldButton = {
    component: CommandIconButton,
    icon: boldIcon,
    command: "bold",
};

const italicButton = {
    component: CommandIconButton,
    icon: italicIcon,
    command: "italic",
};

const underlineButton = {
    component: CommandIconButton,
    icon: underlineIcon,
    command: "underline",
};

const superscriptButton = {
    component: CommandIconButton,
    icon: superscriptIcon,
    command: "superscript",
};

const subscriptButton = {
    component: CommandIconButton,
    icon: subscriptIcon,
    command: "subscript",
};

const eraserButton = {
    component: CommandIconButton,
    icon: eraserIcon,
    command: "removeFormat",
    highlightable: false,
};

export const formatButtons = [
    boldButton,
    italicButton,
    underlineButton,
    superscriptButton,
    subscriptButton,
    eraserButton,
];
