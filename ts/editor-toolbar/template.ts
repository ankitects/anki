import { bridgeCommand } from "anki/bridgecommand";

import IconButton from "./IconButton.svelte";
import DropdownMenu from "./DropdownMenu.svelte";

import paperclipIcon from "./paperclip.svg";
import micIcon from "./mic.svg";
import bracketsIcon from "./code-brackets.svg";
import functionIcon from "./function-variant.svg";
import xmlIcon from "./xml.svg";

function onAttachment(): void {
    bridgeCommand("attach");
}

function onRecord(): void {
    bridgeCommand("record");
}

function onCloze(): void {
    bridgeCommand("cloze");
}

function onHtmlEdit(): void {
    bridgeCommand("htmlEdit");
}

const attachmentButton = {
    component: IconButton,
    icon: paperclipIcon,
    onClick: onAttachment,
};

const recordButton = { component: IconButton, icon: micIcon, onClick: onRecord };

const clozeButton = {
    component: IconButton,
    icon: bracketsIcon,
    onClick: onCloze,
};

const mathjaxButton = {
    component: IconButton,
    icon: functionIcon,
};

const mathjaxMenu = {
    component: DropdownMenu,
    id: "mathjaxMenu",
    menuItems: [{ label: "Foo", onClick: () => console.log("foo") }],
};

const htmlButton = {
    component: IconButton,
    icon: xmlIcon,
    onClick: onHtmlEdit,
};

export const templateButtons = [
    attachmentButton,
    recordButton,
    clozeButton,
    mathjaxButton,
    htmlButton,
];

export const templateMenus = [mathjaxMenu];
