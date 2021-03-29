import { bridgeCommand } from "anki/bridgecommand";

import IconButton from "./IconButton.svelte";
import bracketsIcon from "./code-brackets.svg";
import paperclipIcon from "./paperclip.svg";
import micIcon from "./mic.svg";
import threeDotsIcon from "./three-dots.svg";

function onCloze(): void {
    bridgeCommand("cloze");
}

function onAttachment(): void {
    bridgeCommand("attach");
}

function onRecord(): void {
    bridgeCommand("record");
}

function onMore(): void {
    bridgeCommand("more");
}

export const clozeButton = {
    component: IconButton,
    icon: bracketsIcon,
    onClick: onCloze,
};
export const attachmentButton = {
    component: IconButton,
    icon: paperclipIcon,
    onClick: onAttachment,
};
export const recordButton = { component: IconButton, icon: micIcon, onClick: onRecord };
export const moreButton = {
    component: IconButton,
    icon: threeDotsIcon,
    onClick: onMore,
};
