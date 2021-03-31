import IconButton from "./IconButton.svelte";
import DropdownMenu from "./DropdownMenu.svelte";
import DropdownItem from "./DropdownItem.svelte";
import WithDropdownMenu from "./WithDropdownMenu.svelte";

import { bridgeCommand } from "anki/bridgecommand";
import { lazyProperties } from "anki/lazy";
import * as tr from "anki/i18n";

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

lazyProperties(attachmentButton, {
    title: tr.editingAttachPicturesaudiovideoF3,
});

const recordButton = { component: IconButton, icon: micIcon, onClick: onRecord };

lazyProperties(recordButton, {
    title: tr.editingRecordAudioF5,
});

const clozeButton = {
    component: IconButton,
    icon: bracketsIcon,
    onClick: onCloze,
};

lazyProperties(clozeButton, {
    title: tr.editingClozeDeletionCtrlandshiftandc,
});

const mathjaxButton = {
    component: IconButton,
    icon: functionIcon,
};

const mathjaxMenuId = "mathjaxMenu";

const mathjaxMenu = {
    component: DropdownMenu,
    id: mathjaxMenuId,
    menuItems: [
        { component: DropdownItem, label: "Foo", onClick: () => console.log("foo") },
    ],
};

const mathjaxButtonWithMenu = {
    component: WithDropdownMenu,
    button: mathjaxButton,
    menuId: mathjaxMenuId,
};

const htmlButton = {
    component: IconButton,
    icon: xmlIcon,
    onClick: onHtmlEdit,
};

lazyProperties(htmlButton, {
    title: tr.editingHtmlEditor,
});

export const templateButtons = [
    attachmentButton,
    recordButton,
    clozeButton,
    mathjaxButtonWithMenu,
    htmlButton,
];

export const templateMenus = [mathjaxMenu];
