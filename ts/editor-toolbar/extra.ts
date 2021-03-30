import { bridgeCommand } from "anki/bridgecommand";

import IconButton from "./IconButton.svelte";

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

function onMore(): void {
    bridgeCommand("more");
}

export const attachmentButton = {
    component: IconButton,
    icon: paperclipIcon,
    onClick: onAttachment,
};

export const recordButton = { component: IconButton, icon: micIcon, onClick: onRecord };

export const clozeButton = {
    component: IconButton,
    icon: bracketsIcon,
    onClick: onCloze,
};

export const mathjaxButton = {
    component: IconButton,
    icon: functionIcon,
    onClick: onMore,
};

export const htmlButton = {
    component: IconButton,
    icon: xmlIcon,
    onClick: onMore,
};
